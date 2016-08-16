package com.ernstthuer;

import org.biojava.nbio.core.exceptions.CompoundNotFoundException;
import org.biojava.nbio.core.sequence.DNASequence;
import org.biojava.nbio.core.sequence.compound.NucleotideCompound;

import javax.sound.midi.Sequence;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.ernstthuer.BamHandler.lengthOfReads;

/**
 * Created by ethur on 7/26/16.
 */
public class Gene {

    ArrayList<SNP> snpsOnGene = new ArrayList<>();
    private DNASequence sequence = new DNASequence();
    private String chromosome;
    private int start;
    private int stop;
    private String ident;
    private String orientation = "forward";
    private HashMap<Integer, Codon> codonList = new HashMap<>();
    // storing SNPs on the gene, and reads in a barebone form for reference.  this might be irrelevant due to Sam locus iteration...
    //private List<SNP> geneSNPList = new ArrayList<>();
    private ArrayList<SimpleRead> geneReadList = new ArrayList<>();
    private String ASE;

    public Gene(String chromosome, int start, int stop, String ident) {
        this.chromosome = chromosome;
        this.start = start;
        this.stop = stop;
        this.ident = ident;

        //System.out.println("Created gene " + ident);
    }

    public ArrayList<SNP> getSnpsOnGene() {
        return snpsOnGene;
    }

    public boolean addSNP(SNP snp) {

        if (!snpsOnGene.contains(snp)) {
            snpsOnGene.add(snp);
            return true;
        } else {
            int idx = snpsOnGene.indexOf(snp);
            snpsOnGene.get(idx).increaseAltCoverage();
            if (snpsOnGene.get(idx).getALTcov() > 2) {
                snpsOnGene.get(idx).setValidated(1);
            }
            return false;
        }
    }


    public void findORGCoverageOfSNPs() {
        // trigger this after the SNPs were loaded and the simplified reads are stored on the genes.
        for (SNP snip : snpsOnGene) {

            // only for SNPs with at least 2 observations
            if (snip.isValidated() > 0) {
                for (SimpleRead splRd : geneReadList) {
                    //System.out.println(lengthOfReads);
                    if (splRd.getStart() <= snip.getPosition() && (splRd.getStop()) >= snip.getPosition()) {
                        // overlap, check if the positions are absolut or on gene level
                        snip.increaseORGcov();
                        //System.out.println(" increased SNP coverage with read : " );
                    }
                }
            }
        }

    }

    public void setOrientation(String orientation) {
        this.orientation = orientation;
    }

    public void geneToCodon(String codingSequence){
        // Codon list for all positions, for each position
        //HashMap<Integer, org.biojava.nbio.core.sequence.template.Sequence> CodonList = new HashMap<>();
        // for each codon on sequence
        for(int i = 0; i < codingSequence.length(); i = i+3) {
            //org.biojava.nbio.core.sequence.template.Sequence codon = sequence.getSubSequence(i,i+2).getViewedSequence();
            Codon codon = new Codon(codingSequence.substring(i,i+2));
            for(int j=i;j<(i+3);j++) {
                codonList.put(j, codon);
            }

        }
        }

    public String getAltCodon(int pos, char altChar, String orgCodon){
        StringBuilder newCodon = new StringBuilder(orgCodon);
        newCodon.setCharAt(pos,altChar);
        return newCodon.toString();
    }

    public void findSynonymity(int validationLVL) {
        if (orientation == "forward") {
            geneToCodon(this.sequence.getSequenceAsString());

        }
        if (orientation == "reverse"){
            geneToCodon(this.sequence.getInverse().getSequenceAsString());
        }
            for (SNP snp : snpsOnGene) {
                if (snp.isValidated() >= validationLVL) {

                    // find SNP on codon
                    int relativePosition = snp.getPosition() - start;
                    int remain = relativePosition % 3;


                    Codon refCodon = codonList.get(relativePosition);

                    StringBuilder altSeq = new StringBuilder(refCodon.getSequence());
                    altSeq.setCharAt(remain,snp.getALT());

                    Codon altCodon = new Codon(altSeq.toString());

                    snp.setSynonymous(altCodon.equals(refCodon));

                    //String altCodon = getAltCodon(0, snp.getALT(), refCodon.getSequence());
                        //altCodon = snp.getALT() + altCodon.substring(1,2);
                    //StringBuilder newCodon = new StringBuilder(altCodon);
                    //newCodon.setCharAt(remain, snp.getALT());

                }
            }

    }

    public void evaluateGeneWiseExpression(){

        
    }


    public DNASequence getSequence() {
        return sequence;
    }

    public String getChromosome() {
        return chromosome;
    }

    public int getStart() {
        return start;
    }

    public int getStop() {
        return stop;
    }

    public String getIdent() {
        return ident;
    }


    public void loadSequence(DNASequence fullGenome, boolean fullChrom) {


        //System.out.println(fullGenome.getSubSequence(100 , 102).getSequenceAsString());

        if (fullChrom) {
            try {
                //System.out.println( " Gene from here "+this.start);
                try {

                    this.sequence = new DNASequence(fullGenome.getSubSequence(this.start, this.stop).getSequenceAsString());
                } catch (CompoundNotFoundException e) {
                    System.out.println("Caught error in sequence parsing ");
                    System.out.println(e);
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);
                    sw.toString();
                    System.out.println(sw);
                }

            } catch (Exception e) {
                System.out.println("coldn't parse fasta sequence " + e);
            }
        } else {
            try {
                this.sequence = fullGenome;
            } catch (Exception e) {
                System.out.println("couldn't parse fasta sequence " + e);
            }
        }

    }


    public void addRead(SimpleRead read) {
        this.geneReadList.add(read);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Gene gene = (Gene) o;

        if (start != gene.start) return false;
        if (stop != gene.stop) return false;
        return chromosome != null ? chromosome.equals(gene.chromosome) : gene.chromosome == null;

    }

    @Override
    public int hashCode() {
        int result = chromosome != null ? chromosome.hashCode() : 0;
        result = 31 * result + start;
        result = 31 * result + stop;
        return result;
    }

    public List<SimpleRead> getGeneReadList() {
        return geneReadList;
    }
}




