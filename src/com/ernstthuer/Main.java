package com.ernstthuer;

import org.biojava.nbio.core.sequence.DNASequence;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;

//import org.slf4j.LoggerFactory;

public class Main {


    //ArrayList<Chromosome> chromosomeArrayList = new ArrayList<>();
    // gene List should be static
    static ArrayList<Gene> geneList = new ArrayList<>();
    static ArrayList<SNP> snips = new ArrayList<>();
    static HashMap<String, DNASequence> fasta = new HashMap<>();
    static boolean verbose = true;
    static double bimodalPrimersForNoise = 0.5;


    public static void main(String[] args) {



        System.out.println("Version  0.1");

        // open the input files in sequence,  fasta  gff then bam

        /**
         * The pipeline flows as follows:
         *
         * loading Files,  obligatory are fasta and bam files.
         * load genes to list
         *
         * checking fasta reference against the bam files , store coverage of SNPs
         * add the SNPs to genes
         *
         *
         *
         */

        ArgParser parser = new ArgParser(args);
        // call the argument parser
        //GFF import

        for (FileHandler file : parser.fileList) {
            try {
                if (file.getType() == "GFF" && file.getDirection() == "Input"){
                    //if(file.getType() == "GFF" && file.getDirection() == "Input"){
                    System.out.println("[STATUS]  parsing GFF file");
                try {
                    geneList = ((GFFHandler) file).getGeneList();
                } catch (ClassCastException e) {
                    errorCaller(e);
                }}
            }catch(ClassCastException expected){
                errorCaller(expected);
            }
        }

        // individual loadings
        for (FileHandler file : parser.fileList) {
            if (file.getType() == "FASTA" && file.getDirection() == "Input") {
                System.out.println("Fasta file loading");
                try {
                    fasta = (((FastaHandler) file).readFasta(geneList));
                    //fasta2gene
                    System.out.println("Read fasta");
                } catch (IOException e) {
                    System.out.println(e.getCause());
                    fasta = null;
                }
            }
        }

        for (FileHandler file : parser.fileList) {
            if (file.getType() == "Bam" && file.getDirection() == "Input") {
                System.out.println(" BAM file " +file.getLocale());
                try {

                    if (fasta != null) {
                        BamHandler bhdlr = new BamHandler(file.getLocale(), "Bam", "Input");

                        // to loosen this for threading i should create copies of the genelists
                        bhdlr.readBam(fasta,geneList);
                        bhdlr.findSNPs();
                    }

                } catch (Exception e) {
                    System.out.println(e.getCause());
                    fasta = null;
                }
            }
        }

        // here comes the unification of the genes


        int poscount = 0;
        int unvalidatedCount = 0;
        int totcount = 0;


        // temporary SNPlist


        for(Gene gene:geneList){
            System.out.println(gene.getIdent() + "  :  " +   gene.getGeneReadList().size());
            for(SNP snp: gene.getSnpsOnGene()){
                snips.add(snp);
                if(snp.isValidated() == 1) {

                    // validate for noise
                    if (snp.validateSNP(bimodalPrimersForNoise)) {
                        snp.setValidated(2);

                    }
                    ;


                    if (snp.isValidated() == 2) {
                        poscount++;
                        totcount++;
                        snp.addCoverageToSNPs(gene.getGeneReadList(), 50);
                    }
                    //System.out.println(snp.getALTcov() + " alt : org  " + snp.getORGcov());
                    else {
                        totcount++;
                        unvalidatedCount ++;
                    }
                }
            }
        }

        System.out.println("A total of " + totcount + " SNPs was found,  of which  " + poscount + " Could be validated");

        for (FileHandler file : parser.fileList) {
            if (file instanceof CSVHandler && file.getDirection() == "Output" ) {
                System.out.println("[STATUS] Writing vcf like output to file to " + file.getLocale());
                try {
                    ((CSVHandler) file).writeSNPToVCF(snips,1);
                } catch (Exception e) {
                    errorCaller(e);
                }
            }
        }
    }

    public static void errorCaller(Exception e ){
        if(verbose) {
            System.out.println(e);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            sw.toString();
            System.out.println(sw);
        }
    }
}
