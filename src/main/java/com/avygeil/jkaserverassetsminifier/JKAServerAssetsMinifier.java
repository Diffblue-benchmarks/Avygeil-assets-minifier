package com.avygeil.jkaserverassetsminifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Enumeration;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

public class JKAServerAssetsMinifier {

	public static void main(String[] args) {
		if (args.length < 2) {
			System.out.println("Usage: java-jar assets-minifier.jar <rules> <input folder> [output folder]");
			System.out.println("Rules: path to the file that defines minifying rules");
			System.out.println("This file must contain one rule per line, as follows:");
			System.out.println("    * OutFilename <name with extension>");
			System.out.println("    Name of the minified archive created in the output folder (required)");
			System.out.println("    * InputFile <md5 hash> <filename with extension>");
			System.out.println("    Adds the specified archive as a source archive for the minifier");
			System.out.println("    NB: setting 0 as a hash skips the md5 check for this file");
			System.out.println("    NB2: if no input file is specified at all, all valid archives will be used as source");
			System.out.println("    * EntryWhitelist <wildcard matcher>");
			System.out.println("    Filenames within archives matching this will always be included, unless blacklisted");
			System.out.println("    * EntryBlacklist <wildcard matcher>");
			System.out.println("    Filenames within archives matching this will always be excluded");
			System.out.println("Input folder: the program will look for source zip archives inside that folder");
			System.out.println("Output folder (optional): The program will output the minified zip archive as well as temporary files inside that folder");
			System.out.println("If unset, output folder will be set to input folder");
			return;
		}
		
		Path assetsRulesFilePath = Paths.get(args[0]);
		
		Path inputFolderPath = Paths.get(args[1]);
		
		if (!isPathDirectory(inputFolderPath)) {
			System.out.println("Input folder must be a directory!");
			return;
		}
		
		Path outputFolderPath;
		
		if (args.length == 2) {
			outputFolderPath = inputFolderPath;
		} else {
			outputFolderPath = Paths.get(args[2]);
			
			if (!isPathDirectory(outputFolderPath)) {
				System.out.println("Output folder must be a directory!");
				return;
			}
		}
		
		File assetsRulesFile = assetsRulesFilePath.toFile();
		
		if (!assetsRulesFile.exists()) {
			System.out.println("Assets rules file " + assetsRulesFile.getAbsolutePath() + " does not exist!");
			return;
		}
		
		if (assetsRulesFile.isDirectory()) {
			System.out.println("Assets rules file must be a file!");
			return;
		}
		
		File inputFolder = inputFolderPath.toFile();
		File outputFolder = outputFolderPath.toFile();
		
		AssetsRules assetsRules;
		
		try {
			FileUtils.forceMkdir(outputFolder);
			assetsRules = new AssetsRules(assetsRulesFile, outputFolder);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		System.out.println("Loaded rules from " + assetsRulesFile.getAbsolutePath());
		System.out.println("Minified output: " + assetsRules.getOutFile().getName());
		System.out.println("Input file filters: " + assetsRules.getNumInputFileFilters());
		System.out.println("Whitelist filters: " + assetsRules.getNumWhitelistFilters());
		System.out.println("Blacklist filters: " + assetsRules.getNumBlacklistFilters());
		
		new JKAServerAssetsMinifier(inputFolder, outputFolder, assetsRules).minify();
	}
	
	private static boolean isPathDirectory(Path path) {
		File test = path.toFile();
		return !test.exists() ? test.getName().lastIndexOf('.') == -1 : test.isDirectory();
	}
	
	final File inputFolder;
	final File outputFolder;
	final AssetsRules assetsRules;
	
	public JKAServerAssetsMinifier(File inputFolder, File outputFolder, AssetsRules assetsRules) {
		this.inputFolder = inputFolder;
		this.outputFolder = outputFolder;
		this.assetsRules = assetsRules;
	}
	
	public void minify() {
		System.out.println("Checking " + inputFolder.getAbsolutePath() + " for valid assets files...");
		
		Collection<File> assetsFiles = FileUtils.listFiles(inputFolder, assetsRules, null);
		
		for (File file : assetsFiles) {
			System.out.println("Found " + file.getAbsolutePath());
		}
		
		if (assetsRules.getNumInputFileFilters() != 0 && assetsFiles.size() != assetsRules.getNumInputFileFilters()) {
			System.out.println("Missing assets! Make sure you have all assets files specified in the rules file!");
			return;
		}
		
		File tmpOutFolder = new File(outputFolder, "tmp");
		System.out.println("Temporary extraction folder: " + tmpOutFolder.getAbsolutePath());
		
		try {
			FileUtils.forceMkdir(tmpOutFolder);
			FileUtils.cleanDirectory(tmpOutFolder);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		int extractedFiles = 0;
		
		try {
			for (File file: assetsFiles) {
				System.out.println("Extracting valid files from " + file.getName() + "...");
				extractedFiles += extractArchiveSelectivelyToDir(file, tmpOutFolder, assetsRules);
			}
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		System.out.println("Extracted " + extractedFiles + " files");
		
		System.out.println("Archiving files to " + assetsRules.getOutFile().getAbsolutePath() + "...");
		
		try {
			archiveDirectoryToFile(tmpOutFolder, assetsRules.getOutFile());
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("Done");
		
		FileUtils.deleteQuietly(tmpOutFolder);
	}
	
	private int extractArchiveSelectivelyToDir(File inFile, File outDir, Predicate<String> validEntryPredicate) throws IOException {
		int extractedEntries = 0;
		
		try (ZipFile inZipFile = new ZipFile(inFile, ZipFile.OPEN_READ)) {
	        Enumeration<? extends ZipEntry> entries = inZipFile.entries();
	        
	        while (entries.hasMoreElements()) {
	            ZipEntry entry = entries.nextElement();
	            
	            if (!validEntryPredicate.test(entry.getName())) {
	            	continue;
	            }
	            
	            Path entryPath = outDir.toPath().resolve(entry.getName());
	            
	            if (entry.isDirectory()) {
	                Files.createDirectories(entryPath);
	            } else {
	                Files.createDirectories(entryPath.getParent());
	                
	                try (InputStream in = inZipFile.getInputStream(entry)) {
	                    try (OutputStream out = new FileOutputStream(entryPath.toFile())) {
	                        IOUtils.copy(in, out);
	                        ++extractedEntries;
	                    }
	                }
	            }
	        }
	    }
		
		return extractedEntries;
	}
	
	private void archiveDirectoryToFile(File inDir, File outFile) throws IOException {
		try (FileOutputStream out = new FileOutputStream(outFile)) {
			try (ZipOutputStream outZip = new ZipOutputStream(out)) {
				FileUtils.iterateFiles(inDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE).forEachRemaining(
					f -> {
						try {
							Path targetPath = inDir.toPath().relativize(f.toPath());
							outZip.putNextEntry(new ZipEntry(targetPath.toString()));
							byte[] bytes = FileUtils.readFileToByteArray(f);
							outZip.write(bytes, 0, bytes.length);
							outZip.closeEntry();
						} catch (IOException e) {
							e.printStackTrace();
						}
						
					}
				);
			}
		}
	}
	
}
