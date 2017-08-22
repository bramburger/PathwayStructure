package no.uib.conversion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import static no.uib.conversion.Utils.encoding;

/**
 * This class exports a sif file as generated by PathwayMapper into an igraph
 * data structure.
 *
 * @author Marc Vaudel
 */
public class SifToIGraph {

    /**
     * The main method takes a sif file as generated by PathwayMapper and writes
     * igraph files.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {

        try {

            args = new String[]{"C:\\Projects\\Bram\\graphs\\ProteomeGraphAllEdgeTypes.sif.gz",
                "C:\\Github\\PathwayProjectQueries\\resources\\uniprot_names_human_21.08.17.tab.gz",
                "C:\\Github\\PathwayProjectQueries\\resources\\iGraph\\reactome",
                "reactome_18.08.17"};

            SifToIGraph exportGraphData = new SifToIGraph();

            File sifFile = new File(args[0]);
            File namesMappingFile = new File(args[1]);
            File outputFolder = new File(args[2]);
            String baseName = args[3];

            System.out.println(new Date() + " Parsing uniprot names mapping file");

            HashMap<String, String> proteinNames = Utils.getNamesMap(namesMappingFile);

            System.out.println(new Date() + " Parsing sif file");

            exportGraphData.parseSif(sifFile);

            System.out.println(new Date() + " Exporting results");
            exportGraphData.writeIGraphFiles(outputFolder, baseName, proteinNames);

        } catch (Exception e) {

            e.printStackTrace();

        }
    }

    /**
     * Map of reactions, from input to outputs.
     */
    private HashMap<String, HashSet<String>> reaction = new HashMap<>();
    /**
     * Map of regulators, from input to outputs.
     */
    private HashMap<String, HashSet<String>> regulation = new HashMap<>();
    /**
     * Map of catalyzers, from input to outputs.
     */
    private HashMap<String, HashSet<String>> catalysis = new HashMap<>();
    /**
     * Map of complexes, from input to outputs.
     */
    private HashMap<String, HashSet<String>> complex = new HashMap<>();
    /**
     * Set of all nodes.
     */
    private HashSet<String> allNodes = new HashSet<>();

    /**
     * Parses a sif file and populates the maps.
     *
     * @param sifFile the sif file
     *
     * @throws IOException exception thrown if an error occurred while reading
     * the sif file.
     */
    private void parseSif(File sifFile) throws IOException {

        HashMap<String, HashSet<String>> tempReaction = new HashMap<>();

        InputStream fileStream = new FileInputStream(sifFile);
        InputStream gzipStream = new GZIPInputStream(fileStream);
        Reader decoder = new InputStreamReader(gzipStream, encoding);

        try (BufferedReader br = new BufferedReader(decoder)) {

            String line;
            while ((line = br.readLine()) != null) {

                if (line.length() > 10) {

                    int firstSpaceIndex = line.indexOf(' ');
                    String accession = line.substring(0, firstSpaceIndex);

                    allNodes.add(accession);

                    char functionIn = line.charAt(firstSpaceIndex + 1);
                    char functionOut = line.charAt(firstSpaceIndex + 2);

                    String targetsLine = line.substring(10);
                    String[] targets = targetsLine.split(" ");

                    for (String target : targets) {

                        if (!target.equals(accession)) {

                            if (functionIn == 'i' && functionOut == 'o') {

                                HashSet<String> outputs = tempReaction.get(accession);

                                if (outputs == null) {

                                    outputs = new HashSet<>(1);
                                    tempReaction.put(accession, outputs);

                                }

                                outputs.add(target);

                            } else if (functionIn == 'r' && functionOut == 'o') {

                                HashSet<String> outputs = regulation.get(accession);

                                if (outputs == null) {

                                    outputs = new HashSet<>(1);
                                    regulation.put(accession, outputs);

                                }

                                outputs.add(target);

                            } else if (functionIn == 'c' && functionOut == 'o') {

                                HashSet<String> outputs = catalysis.get(accession);

                                if (outputs == null) {

                                    outputs = new HashSet<>(1);
                                    catalysis.put(accession, outputs);

                                }

                                outputs.add(target);

                            } else if (functionIn == 'c' && functionOut == 'n') {

                                HashSet<String> outputs = complex.get(accession);

                                if (outputs == null) {

                                    outputs = new HashSet<>(1);
                                    complex.put(accession, outputs);

                                }

                                outputs.add(target);

                            }
                        }
                    }
                }
            }
        }

        for (String accession : tempReaction.keySet()) {

            HashSet<String> targets = tempReaction.get(accession);
            HashSet<String> complexes = complex.get(accession);

            if (complexes == null) {

                reaction.put(accession, targets);

            } else {

                HashSet<String> filteredTargets = targets.stream()
                        .filter(target -> !complexes.contains(target))
                        .collect(Collectors.toCollection(HashSet::new));

                if (!filteredTargets.isEmpty()) {

                    reaction.put(accession, filteredTargets);

                }
            }
        }

        populateNodesList(reaction);
        populateNodesList(catalysis);
        populateNodesList(regulation);
        populateNodesList(complex);
    }

    /**
     * Populates the nodes with the accessions in the given map.
     *
     * @param accessionsMap the accessions map
     */
    private void populateNodesList(HashMap<String, HashSet<String>> accessionsMap) {

        accessionsMap.entrySet().stream()
                .forEach(entry -> {
                    allNodes.add(entry.getKey());
                    allNodes.addAll(entry.getValue());
                });
    }

    /**
     * Write the igraph files.
     *
     * @param folder the destination folder
     * @param baseFileName the base name for the edges and vertices files
     * @param proteinNames the accession to protein name map
     *
     * @throws IOException exception thrown if an error occurred while writing
     * the file
     */
    private void writeIGraphFiles(File folder, String baseFileName, HashMap<String, String> proteinNames) throws IOException {

        File edgeFile = new File(folder, baseFileName + "_edges");

        FileOutputStream outputFileStream = new FileOutputStream(edgeFile);
        GZIPOutputStream outputGzipStream = new GZIPOutputStream(outputFileStream);
        OutputStreamWriter outputEncoder = new OutputStreamWriter(outputGzipStream, encoding);

        try (BufferedWriter bw = new BufferedWriter(outputEncoder)) {

            bw.write("from to type");
            bw.newLine();

            Utils.writeEdges(bw, reaction, "Reaction");
            Utils.writeEdges(bw, catalysis, "Catalysis");
            Utils.writeEdges(bw, regulation, "Regulation");
            Utils.writeEdges(bw, complex, "Complex");

        }

        File nodesFile = new File(folder, baseFileName + "_vertices");

        outputFileStream = new FileOutputStream(nodesFile);
        outputGzipStream = new GZIPOutputStream(outputFileStream);
        outputEncoder = new OutputStreamWriter(outputGzipStream, encoding);

        try (BufferedWriter bw = new BufferedWriter(outputEncoder)) {

            bw.write("id\tname");
            bw.newLine();

            bw.write(
                    allNodes.stream()
                            .sorted()
                            .map(accession -> Utils.getNodeLine(accession, proteinNames))
                            .collect(Collectors.joining(System.lineSeparator()))
            );
        }
    }
}
