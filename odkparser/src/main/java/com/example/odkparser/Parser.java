/**
 * Kenneth Ma
 * 12/16/21
 *
 * This is the parser class that parses training logs produced by the ODK trainingLogger.js class.
 * There are a number of configurable fields below that must have continuity with the logging class
 * for this parser to work.
 *
 * Please place your logfiles in the logFolder. These will be parsed in alphabetically order.
 * Parsed output files will be placed in the parsedLogs folder.
 *
 * Note: the COMBINE_DAY_OUTPUT flag can be used to write all of the day's output to one file.
 */

package com.example.odkparser;

import java.io.File;
import java.io.PrintStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Scanner;

public class Parser {

    // UPDATE: Configure these with the trainingLogger.js class
    public static final String LOG_PREFIX = "TRAINING_LOG";
    public static final String LOG_DETAILS_SEPARATOR = "--";

    // UPDATE: All keywords that denote the end of a user flow
    public static final String[] USER_FLOW_END = {
            "Edit", "Delete",
            "Edit Log", "Delete Log",
            "Add Refrigerator", "Edit Refrigerator", "Delete Refrigerator", "Move Refrigerator", "Delete Move",
            "Edit Temperature Data", "Delete Temperature Data",
            "Edit Refrigerator Status",
            "Edit Cold Room Status",
            "Add Maintenance Record",
            "Add Cold Room", "Edit Cold Room", "Delete Cold Room",
            "Edit Facility", "Delete Facility"
    };

    // UPDATE: True if you want to write all output to one file per day, false if you want to create
    // a new file on every run.
    public static final boolean COMBINE_DAY_OUTPUT = false;

    // NOTE: Ensure your working directory is set correctly. The relative path builds on the WD
    // to find the input and output folders.
    // > Try running the main method below
    // > Under run configurations, click "Edit Configuration"
    // > In working directory, ensure the path ends in "ODKParser"
    public static final String RELATIVE_PATH =
            "odkparser" + File.separator +
            "src" + File.separator +
            "main" + File.separator +
            "java" + File.separator +
            "com" + File.separator +
            "example" + File.separator +
            "odkparser" + File.separator;

    // Parser Input/output
    public static final String LOG_FOLDER_PATH = RELATIVE_PATH + "logFolder";
    public static final String PARSED_LOGS_PATH = RELATIVE_PATH + "parsedLogs";
    public static final String PARSED_LOGS_PREFIX = File.separator + "PARSED_";

    // Class constants
    public static final double NULL_START_TIME = 0;

    // Class fields
    public static PrintStream outputStream;
    public static double actionSequenceStartTime = NULL_START_TIME; // if NZ, currently parsing a sequence

    public static void main(String[] args) {
        try {
            createOutputFile();
            printOutputHeader();
            parseLogFilesInFolder(new File(LOG_FOLDER_PATH));

            outputStream.close();
        } catch (Exception e) {
            System.err.println("Error parsing log files.");
            System.err.println(e);
        }
    }

    /**
     * Creates an output file with the filename: output_prefix + current_date
     */
    public static void createOutputFile() {
        try {
            String currentDate;
            if (COMBINE_DAY_OUTPUT) {
                currentDate = LocalDate.now().toString();
            } else {
                currentDate = LocalDateTime.now().toString().replace(":", ".");
                // cannot use colons in filename
            }

            File outputFile = new File(PARSED_LOGS_PATH + PARSED_LOGS_PREFIX + currentDate);
            outputFile.createNewFile();
            outputStream = new PrintStream(outputFile);
        } catch (Exception e) {
            System.err.println("Error creating output file.");
            System.err.println(e);
        }
    }


    //
    // Parsing methods
    //

    /**
     * Parses all log files in given folder and prints to output
     * @param folder - the log folder
     */
    public static void parseLogFilesInFolder(File folder) {
        File[] logFiles = folder.listFiles();
        Arrays.sort(logFiles);

        for (final File fileEntry : logFiles) {
            if (fileEntry.isDirectory()) {
                parseLogFilesInFolder(fileEntry);
            } else {
                parseFile(fileEntry);
            }
        }
    }

    /**
     * Parses a single log file and prints to output. Note that the last action sequence may not be
     * properly parsed because the odk logger flushes at undetermined times, and may begin logging
     * to a new file in the middle of an action sequence.
     * @param file - the log file
     */
    public static void parseFile(File file) {
        try {
            printFileHeader(file.getName());
            Scanner sc = new Scanner(file);

            while (sc.hasNextLine()) {
                if (actionSequenceStartTime == NULL_START_TIME) {
                    printFirstLineAndSetStartTime(sc);
                }
                if (actionSequenceStartTime != NULL_START_TIME) {
                    parseRestOfActionSequence(sc);
                }
            }
        } catch (Exception e) {
            System.err.println("There was an error parsing this file");
            System.err.println(file.getName());
            System.err.println(e);
        }
    }

    /**
     * Finishes parsing the current action sequence, then sets action sequence start time to 0
     * @param sc - the scanner for the current file
     */
    public static void parseRestOfActionSequence(Scanner sc) {

        double prevTime = actionSequenceStartTime;  // store prev time to compute action times
        while (sc.hasNextLine()) {
            String nextLine = sc.nextLine();
            String[] tokens = nextLine.split("/");
            if (tokens.length > 2 && tokens[2].equals(LOG_PREFIX)) {
                System.out.println(nextLine);

                double currTime = parseTime(tokens[3]);
                double actionTime = (currTime - prevTime) / 1000;

                boolean end = printTrainingLogAndCheckIfEnd(actionTime, tokens[4], tokens[5]);

                if (end) {
                    double sequenceTime = (currTime - actionSequenceStartTime) / 1000;
                    printActionFooter(sequenceTime);
                    actionSequenceStartTime = NULL_START_TIME;
                    return;
                }

                prevTime = currTime;
            }
        }
    }

    /**
     * Parse the time in milliseconds from the training log line
     * @param timeString - the time string token
     * @return the time (ms) since Jan 1, 1970 00:00:00 UTC
     */
    public static double parseTime(String timeString) {
        return Double.parseDouble(timeString.replace("Time=", ""));
    }


    //
    // Printing methods
    //

    /**
     * Writes the output header to the output file, including the current date-time
     */
    public static void printOutputHeader() {
        try {
            String header = "===================================" + "\n" +
                    "\t" + "PARSED TRAINING LOGS" + "\n" +
                    "\t" + LocalDateTime.now().toString() + "\n" +
                    "\t" + "Use with ODK trainingLogger.js" + "\n" +
                    "===================================";

            outputStream.println(header);
        } catch (Exception e) {
            System.err.println("Error writing output file header.");
            System.err.println(e);
        }
    }

    /**
     * Print a file header, denoting a new log file, to output
     * @param fileName - the name of the file
     */
    public static void printFileHeader(String fileName) {
        String header = "\n" + "-----------------------------------" + "\n" +
                "Parsing New File: " + fileName;

        outputStream.println(header);
    }

    /**
     * Print the first, parsed training log line and extract the time for action time computation.
     * @param sc - the scanner used to read this log file
     */
    public static void printFirstLineAndSetStartTime(Scanner sc) {
        while (sc.hasNextLine()) {
            String nextLine = sc.nextLine();

            String[] tokens = nextLine.split("/");
            if (tokens.length > 2 && tokens[2].equals(LOG_PREFIX)) {
                System.out.println(nextLine);

                printActionHeader();
                boolean end = printTrainingLogAndCheckIfEnd(0, tokens[4], tokens[5]);

                if (end) {
                    printActionFooter(0);
                    actionSequenceStartTime = NULL_START_TIME;
                } else {
                    actionSequenceStartTime = parseTime(tokens[3]);
                }
                return;
            }
        }
    }

    /**
     * Print the parsed training log into to output and check if it's the end of an action sequence.
     * @param time - the log time
     * @param action - the log action type (usually CLICKED)
     * @param details - the log details (usually the element text)
     * @return true if details is one of the user flow end keywords, false otherwise
     */
    public static boolean printTrainingLogAndCheckIfEnd(double time, String action, String details) {
        outputStream.println("\t" + action + "\t[" + time + " (s)]:");
        String[] tokens = details.split(LOG_DETAILS_SEPARATOR);
        outputStream.println("\t * " + tokens[0]);
        for (int i = 1; i < tokens.length; i++) {
            outputStream.println("\t\t-- " + tokens[i]);
        }

        return Arrays.asList(USER_FLOW_END).contains(details);
    }

    /**
     * Print the footer of an action sequence to output, including the total time taken
     * @param time - the action sequence time
     */
    public static void printActionFooter(double time) {
        String output = "\t-------------------------------" + "\n" +
                "\tAction sequence took " + time + " (s)";
        outputStream.println(output);
    }

    /**
     * Print the header of an action sequence to output
     */
    public static void printActionHeader() {
        String output = "\n\tLogging New Action Sequence...\n" +
        "\t-------------------------------";
        outputStream.println(output);
    }
}