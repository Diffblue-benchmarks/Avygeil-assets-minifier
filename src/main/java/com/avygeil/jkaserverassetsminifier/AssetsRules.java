package com.avygeil.jkaserverassetsminifier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.StringUtils;

public class AssetsRules implements IOFileFilter, Predicate<String> {
	
	File outFile;
	final Map<String, String> inputFileFilter = new HashMap<>();
	final List<String> entryWhitelistFilter = new ArrayList<>();
	final List<String> entryBlacklistFilter = new ArrayList<>();
	
	public AssetsRules(List<String> lines, File outputFolder) throws IOException {
		parseRulesFile(lines, outputFolder);
	}
	
	private void parseRulesFile(List<String> lines, File outputFolder) throws IOException {		
		for (String line : lines) {
			int splitMax;
			
			if (line.toLowerCase().startsWith("inputfile ")) {
				splitMax = 3;
			} else if ( line.toLowerCase().startsWith("outfilename ") ||
					line.toLowerCase().startsWith("entrywhitelist ") ||
					line.toLowerCase().startsWith("entryblacklist ")) {
				splitMax = 2;
			} else {
				continue;
			}
			
			String[] tokens = StringUtils.split(line.trim(), " ", splitMax);
			
			// ignore empty lines
			if (tokens.length == 0) {
				continue;
			}
			
			if (tokens[0].equalsIgnoreCase("OutFilename") && tokens.length == 2) {
				// only use the name part in case it's a directory structure
				Path outFilePath = Paths.get(tokens[1]);
				outFile = outputFolder.toPath().resolve(outFilePath.getFileName()).toFile();
			} else if (tokens[0].equalsIgnoreCase("InputFile") && tokens.length == 3) {
				inputFileFilter.put(tokens[2].toLowerCase(), tokens[1].toLowerCase());
			} else if (tokens[0].equalsIgnoreCase("EntryWhitelist") && tokens.length == 2) {
				entryWhitelistFilter.add(tokens[1]);
			} else if (tokens[0].equalsIgnoreCase("EntryBlacklist") && tokens.length == 2) {
				entryBlacklistFilter.add(tokens[1]);
			}
		}
		
		if (outFile == null) {
			throw new RuntimeException("Rules file must contain a valid OutFilename!");
		}
	}
	
	public File getOutFile() {
		return outFile;
	}
	
	public int getNumInputFileFilters() {
		return inputFileFilter.size();
	}
	
	public int getNumWhitelistFilters() {
		return entryWhitelistFilter.size();
	}
	
	public int getNumBlacklistFilters() {
		return entryBlacklistFilter.size();
	}
	
	public boolean acceptInputFile(File file) {
		// if no filter is set, accept all
		if (inputFileFilter.isEmpty()) {
			return true;
		}
		
		String name = file.getName().toLowerCase();
		
		if (!inputFileFilter.containsKey(name)) {
			return false;
		}
		
		String md5 = inputFileFilter.get(name);
		
		// if hash is set to 0, don't check for it at all
		if (md5.equals("0")) {
			return true;
		}
		
		try {
			if (md5.equalsIgnoreCase(DigestUtils.md5Hex(FileUtils.readFileToByteArray(file)))) {
				return true;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	public boolean acceptEntryFilename(String filename) {
		for (String blacklistMatcher : entryBlacklistFilter) {
			if (FilenameUtils.wildcardMatch(filename, blacklistMatcher, IOCase.INSENSITIVE)) {
				return false;
			}
		}
		
		for (String whitelistMatcher : entryWhitelistFilter) {
			if (FilenameUtils.wildcardMatch(filename, whitelistMatcher, IOCase.INSENSITIVE)) {
				return true;
			}
		}
		
		return false;
	}

	@Override
	public boolean accept(File inputFile) {
		return acceptInputFile(inputFile);
	}

	@Override
	public boolean accept(File inputDir, String name) {
		return false;
	}

	@Override
	public boolean test(String entryFilename) {
		return acceptEntryFilename(entryFilename);
	}
}
