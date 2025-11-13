package com.ibm.initialize.utils;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AppUtils {
	
	  public static String basicAuth(String user, String pass) {
	        String token = user + ":" + pass;
	        String encoded = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
	        return "Basic " + encoded;
	    }
	    /**
	     * Extract a ZIP file to a directory, safely (prevents Zip Slip).
	     */
	     public static void extractZip(Path zipFile, Path targetDir) throws IOException {
	        try (InputStream fis = Files.newInputStream(zipFile);
	             BufferedInputStream bis = new BufferedInputStream(fis);
	             ZipInputStream zis = new ZipInputStream(bis)) {

	            ZipEntry entry;
	            byte[] buffer = new byte[8192];

	            while ((entry = zis.getNextEntry()) != null) {
	                String entryName = entry.getName();

	                // Normalize path to avoid Zip Slip
	                Path resolved = targetDir.resolve(entryName).normalize();
	                if (!resolved.startsWith(targetDir)) {
	                    throw new IOException("Blocked Zip Slip attempt: " + entryName);
	                }

	                if (entry.isDirectory()) {
	                    Files.createDirectories(resolved);
	                } else {
	                    Files.createDirectories(resolved.getParent());
	                    try (OutputStream os = Files.newOutputStream(resolved,
	                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
	                        int read;
	                        while ((read = zis.read(buffer)) != -1) {
	                            os.write(buffer, 0, read);
	                        }
	                    }
	                }

	                zis.closeEntry();
	            }
	        }
	    }


	     public static Optional<String> getFileNameFromContentDisposition(HttpResponse<?> response) {
	        Optional<String> cd = response.headers().firstValue("Content-Disposition");
	        if (cd.isEmpty()) return Optional.empty();

	        String value = cd.get();
	        String token = "filename=";
	        int idx = value.toLowerCase().indexOf(token);
	        if (idx >= 0) {
	            String fn = value.substring(idx + token.length()).trim();
	            if (fn.startsWith("\"") && fn.endsWith("\"") && fn.length() >= 2) {
	                fn = fn.substring(1, fn.length() - 1);
	            }
	            fn = fn.replace("/", "_").replace("\\", "_");
	            if (!fn.isBlank()) return Optional.of(fn);
	        }
	        return Optional.empty();
	    }
	    
	     public static Path resolveOutputPath(String outArg, String suggestedFileName) throws IOException {
	        Path p = Paths.get(outArg);
	        // If it's an existing directory, or ends with a separator, treat as directory
	        if (Files.isDirectory(p) || looksLikeDirectory(outArg)) {
	            return p.resolve(suggestedFileName);
	        }
	        // If parent is specified, ensure exists
	        Path parent = p.getParent();
	        if (parent != null && !Files.exists(parent)) {
	            Files.createDirectories(parent);
	        }
	        return p;
	    }
	    
	     public static boolean looksLikeDirectory(String s) {
	        // crude check for a trailing separator
	        return s.endsWith("/") || s.endsWith("\\");
	    }
	    
	    
	     public static void requireReadableFile(Path p, String label) throws IOException {
	        if (!Files.exists(p)) throw new FileNotFoundException(label + " not found: " + p);
	        if (!Files.isRegularFile(p)) throw new IOException(label + " is not a file: " + p);
	        if (!Files.isReadable(p)) throw new IOException(label + " is not readable: " + p);
	    }
	    
	     public static String detectContentType(Path path) {
	        try {
	            String ct = Files.probeContentType(path);
	            return (ct != null) ? ct : "application/octet-stream";
	        } catch (IOException e) {
	            return "application/octet-stream";
	        }
	    }
	    
	     public static void renameExtensionFiles(Path path) {
	    	
	    	if (!Files.exists(path) || !Files.isDirectory(path)) {
	            System.out.println(":x: Invalid folder path: " + path);
	            return;
	        }
	        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
	            for (Path folder : stream) {
	            	String fileName1 = folder.getFileName().toString();
	                if (!Files.isDirectory(folder) && (folder.getFileName().toString().toLowerCase().endsWith(".brmx") || folder.getFileName().toString().toLowerCase().endsWith(".brdx"))) {
	                   
	                    String extension = null;
	                    String newName= null;
	                    if(folder.toString().endsWith(".brmx")) {
	                    	extension="*.brmx";
	                    	newName = "ModelExtension.brmx";
	                    } else {
	                    	extension="*.brdx";
	                    	newName = "DataExtension.brdx";
	                    }
	                    
	                            Path newFile = folder.resolveSibling(newName);
	                            
	                            Files.move(folder, newFile, StandardCopyOption.REPLACE_EXISTING);
	                            
	                        }
	                   
	                }
	            }
	         catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
	        
	       
	    }
	    public static  Map<String, String> mapArguments(String[] args) {
	    	
	    	 Map<String, String> params = new HashMap<>();
	         params.put("url", null);
	         params.put("username", null);
	         params.put("password", null);
	         params.put("dataSource", null);
	         params.put("locale", null);
	         // Parse arguments of the form --key=value
	         if(args.length>0) {
	         for (String arg : args) {
	        	 arg = arg.trim();
	             if (arg.startsWith("--") && arg.contains("=")) {
	                 String[] parts = arg.substring(2).split("=", 2);
	                 if (parts.length == 2 && params.containsKey(parts[0])) {
	                     params.put(parts[0], parts[1]);
	                 }
	             }
	         }
	         System.out.println("Arguments passed are: ");
	         for(Entry<String, String> entry : params.entrySet()) {
	        	 if(null != entry.getValue()) {
	        		 if(entry.getKey().equalsIgnoreCase("password")) {
	        			 System.out.println(entry.getKey() + " : *******" );
	        		 } else {
	        	System.out.println(entry.getKey() + " : " +entry.getValue());
	        		 }
	        	 }
	         }
	         } else {
	        	 System.out.println("No arguments passed. Will take default values");
	         }
	         return params ;
	    }
	    
	    public static boolean isValidUrl(String input) {
	    	
	    	String URL_PATTERN = "^(https?)://([a-zA-Z0-9.-]+):(\\d+)$";
	    	Pattern pattern = Pattern.compile(URL_PATTERN);
	    	  if (input == null || input.trim().isEmpty()) {
	              return false;
	          }
	          Matcher matcher = pattern.matcher(input);
	          return matcher.matches();
	    }

}
