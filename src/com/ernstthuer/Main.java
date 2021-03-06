package com.ernstthuer;

import org.biojava.nbio.core.sequence.DNASequence;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;


public class Main {

    // gene List should be static
    static ArrayList<Gene> geneList = new ArrayList<>();
    private static ArrayList<SNP> snips = new ArrayList<>();
    private static HashMap<String, DNASequence> fasta = new HashMap<>();
    private static boolean verbose = true;
    static HashMap<String, String> codonConversion = new HashMap<>();
    private static int numThreads = 10;

    static float alphaModifier;
    static float betaModifier ;

     // bimodial primers for noise correction
    static double bimodalPrimersForNoise = 0.5;

    // strong informative primers for the assumption of 0.5 ratio expression
    static double strongCentralInformativePrimers = 25;
    static int minCovThreshold = 50;

    // 0= initiation state ; 1 = repeated observation  ; 2 = significant true positive expectation
    private static int validationLVL = 2;


    public static void main(String[] args) {


        System.out.println("Running ASEbyBayes Version  0.24");
        populateCodonConversion();


        /**
         * The pipeline flows as follows:
         *
         * loading Files,  obligatory are fasta and bam files.
         * load genes to list
         *
         * checking fasta reference against the bam files , store coverage of SNPs
         * add the SNPs to genes
         *
         *The flow of data:
         * import of fasta and gff
         * import of bam
         *
         * parsing gene by gene for SNPs,  storing only occurances where more than 2 mappings were found as hard minimum threshold.
         * SNPs are validated from SNP class functions,
         *
         * Each SNP is given a likelihood to be part of a given hypothesis, hypothesis are adapted on the flow.
         * An iterative loop is implemented, until convergence
         *
         *
         * Important points are that the SNPs are carried until a late stage, enabling reclassification instead of discarding the SNPs.
         *
         *
         *
         */

        ArgParser parser = new ArgParser(args);
        // call the argument parser
        // GFF import   Fasta reference Input


        // Try block to open obligatory Input files,  GFF import   Fasta reference Input ,  casts as GFFHandler and FastaHandler objects
        try {
            GFFHandler gffHandler = (GFFHandler) parser.returnType("GFF", "Input");
            System.out.println("[STATUS] loading Fasta file");
            FastaHandler fastaHandler = (FastaHandler) parser.returnType("FASTA", "Input");
            geneList = gffHandler.getGeneList();
            fasta = fastaHandler.readFasta(geneList);
            try {
                // test is empty
                geneList.get(0).getSequence().getLength();
            } catch (IndexOutOfBoundsException e) {
                System.out.println("[ERROR] no features found in GFF file,   try specify  -F gene ");
                System.exit(1);
            }

        } catch (ClassCastException e) {
            errorCaller(e);
        } catch (IOException IOex) {
            System.out.println("[ERROR] File not Found exception at GFF reader input");
            errorCaller(IOex);
        }
        // Block to read information from the BAM files,   depends on whether a vcf input file was given ( SNPs are known) and simple quantification has to
        // be carried out,  or no VCF is provided, and SNP calling is carried out.
        if (parser.isExistingSNPinfo()) {
            // vcf was provided,  overrides SNP calling functionality
            CSVHandler csvHandler = (CSVHandler) parser.returnType("CSV", "INPUT");
            csvHandler.readVCF(); // this primes the genes with full SNP lists
        }


        ArrayList<BamHandler> listOfBamFilesFromParserForBamThreader = new ArrayList<>();

        for (FileHandler file : parser.fileList) {
            if (file.getType().equals("Bam") && file.getDirection().equals("Input")) {
                //implement Threading for multisample processing
                System.out.println("[STATUS] Loading BAM file " + file.getLocale());
                listOfBamFilesFromParserForBamThreader.add(new BamHandler(file.getLocale(), "Bam", "Input", fasta));
            }
        }

        BamThreader bamThreader = new BamThreader(listOfBamFilesFromParserForBamThreader, geneList, numThreads);

        geneList = bamThreader.getOutputGeneArrayList();


        // Hypothesis implementation via Hypothesis tester
        HypothesisFactory hypothesisFactory = new HypothesisFactory(geneList);
        geneList = hypothesisFactory.getGeneList();


//
        for (Gene gene : geneList) {

            //System.out.println(gene.getSnpsOnGene().size());
            for (SNP snp: gene.getSnpsOnGene()
                 ) {
                //System.out.println(snp.getPosition());
                snips.add(snp);
            }
//
//            System.out.println("gene " + gene.getIdent() + " ");
//            gene.evaluateAvailableHypothesis();
//            snips.addAll(gene.getInformativeSnpsOnGene());
//
        }

        ArrayList<Hypothesis> hypothesises = hypothesisFactory.getTestableHypothese();
        GeneEvaluatorFactory geneEvaluatorFactory = new GeneEvaluatorFactory(geneList,hypothesises);
        ArrayList<ResultHypothesis> resultHypothesises = geneEvaluatorFactory.getResultHypothesis();


        // clean the list
        resultHypothesises.removeAll(Collections.singleton(null));

//        for (ResultHypothesis res: resultHypothesises
//             ) {
//            if (! (res == null)) {
//                System.out.println(res.getName() + "  " + res.getGene() + " " + res.getProb());
//            }
//        }

        try {
            CSVHandler csvHandler = (CSVHandler) parser.returnType("VCF", "Output");
            FastaHandler fastaHandler = (FastaHandler) parser.returnType("FASTA", "Output");
            int minThresh = 2;

            //csvHandler.writeSNPToVCF(snips, listOfBamFilesFromParserForBamThreader.size());

            //reimplemented the output into a fastasilencer class
            //System.out.println("[STATUS] Writing Silenced fasta sequence of SNPs to file : " + fastaHandler.getLocale());
            //FastaSilencer fastaSilencer = new FastaSilencer(snips, fasta, fastaHandler.getLocale());

            if(!fastaHandler.getLocale().equals("")) {
                System.out.println("Writing to file here : " + fastaHandler.getLocale());
                new FastaSilencer(snips, fasta, fastaHandler.getLocale());
            }
        } catch (ClassCastException e) {
            errorCaller(e);
        }


        try {
            CSVHandler outwriter = (CSVHandler) parser.returnType("outFinal","Output");

            try {
                outwriter.writeResultsToTsv(resultHypothesises);

            }catch (NullPointerException e){
                System.out.println("Could not create output file");
                e.printStackTrace();
            }



        }catch (ClassCastException e) {
            errorCaller(e);
        }
        System.out.println("[STATUS] Run complete");
    }
    //*///DISABLED FOR TESTING

    private static void errorCaller(Exception e) {
        if (verbose) {
            //System.out.println(e);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            //sw.toString();
            System.out.println(sw);
        }
    }


    private static void populateCodonConversion() {
        codonConversion.put("TCT", "Ser");
        codonConversion.put("TAT", "Tyr");
        codonConversion.put("TGT", "Cys");
        codonConversion.put("TTT", "Phe");
        codonConversion.put("TTC", "Phe");
        codonConversion.put("TCC", "Ser");
        codonConversion.put("TAC", "Tyr");
        codonConversion.put("TGC", "Cys");
        codonConversion.put("TTA", "Leu");
        codonConversion.put("TCA", "Ser");
        codonConversion.put("TAA", "TER");
        codonConversion.put("TGA", "TER");
        codonConversion.put("TTG", "Leu");
        codonConversion.put("TCG", "Ser");
        codonConversion.put("TAG", "TER");
        codonConversion.put("TGG", "Trp");
        codonConversion.put("CTT", "Leu");
        codonConversion.put("CCT", "Pro");
        codonConversion.put("CAT", "His");
        codonConversion.put("CGT", "Arg");
        codonConversion.put("CTC", "Leu");
        codonConversion.put("CCC", "Pro");
        codonConversion.put("CAC", "His");
        codonConversion.put("CGC", "Arg");
        codonConversion.put("CTA", "Leu");
        codonConversion.put("CCA", "Pro");
        codonConversion.put("CAA", "Gln");
        codonConversion.put("CGA", "Arg");
        codonConversion.put("CTG", "Leu");
        codonConversion.put("CCG", "Pro");
        codonConversion.put("CAG", "Gln");
        codonConversion.put("CGG", "Arg");
        codonConversion.put("ATT", "Ile");
        codonConversion.put("ACT", "Thr");
        codonConversion.put("AAT", "Asn");
        codonConversion.put("AGT", "Ser");
        codonConversion.put("ATC", "Ile");
        codonConversion.put("ACC", "Thr");
        codonConversion.put("AAC", "Asn");
        codonConversion.put("AGC", "Ser");
        codonConversion.put("ATA", "Ile");
        codonConversion.put("ACA", "Thr");
        codonConversion.put("AAA", "Lys");
        codonConversion.put("AGA", "Arg");
        codonConversion.put("ATG", "Met");
        codonConversion.put("ACG", "Thr");
        codonConversion.put("AAG", "Lys");
        codonConversion.put("AGG", "Arg");
        codonConversion.put("GTT", "Val");
        codonConversion.put("GCT", "Ala");
        codonConversion.put("GAT", "Asp");
        codonConversion.put("GGT", "Gly");
        codonConversion.put("GTC", "Val");
        codonConversion.put("GCC", "Ala");
        codonConversion.put("GAC", "Asp");
        codonConversion.put("GGC", "Gly");
        codonConversion.put("GTA", "Val");
        codonConversion.put("GCA", "Ala");
        codonConversion.put("GAA", "Glu");
        codonConversion.put("GGA", "Gly");
        codonConversion.put("GTG", "Val");
        codonConversion.put("GCG", "Ala");
        codonConversion.put("GAG", "Glu");
        codonConversion.put("GGG", "Gly");

    }


}
