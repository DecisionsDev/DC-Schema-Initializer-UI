package com.ibm.initialize.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublisher;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ibm.initialize.controller.StatusController;
import com.ibm.initialize.utils.AppUtils;
import com.ibm.initialize.utils.CurlUtil;
import com.ibm.initialize.utils.MultipartBodyPublisher;


@Service
public class SchemaInitializerService {
	
    private static final String END_POINT = "/decisioncenter-api/v1/DBAdmin";
    private static final String DEFAULT_OUTPUT_DIR = "extracted-model-extensions";
    private static final String DEFAULT_OUTPUT_SCRIPT = "createSchema.sql"; 
    private static final String DEFAULT_MODEL_PATH = "./extracted-model-extensions/ModelExtension.brmx";
    private static final String DEFAULT_DATA_PATH  = "./extracted-model-extensions/DataExtension.brdx";
    private static final String DEFAULT_LOCALE ="en_US";
    private static final String DEFAULT_DATA_SOURCE="jdbc/ilogDataSource";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);

	public void initializeSchema(String url, String username, String password, String dataSource, String locale, MultipartFile model, MultipartFile data) {
		long startTime = System.currentTimeMillis();
		url = url + END_POINT ;
		dataSource = (dataSource != null) ? dataSource : DEFAULT_DATA_SOURCE ;
		locale = (locale != null) ? locale : DEFAULT_LOCALE ;
    	if(getModelExtension(url, username, password, dataSource, model, data)) {
    		if(prepareSchemaScript(url, username, password, dataSource)) {
    			if(executeCreateSchema(url, username, password, dataSource)) {
    				int attempt  =1;
    		    	int maxRetries =10 ;
    		    	boolean executed = false;
    		    	 while (attempt <= maxRetries) {
    		    		 executed = isExecuted(url, username, password, dataSource);
    		    	if(executed) {
    		    		break;
    		    	} else {
    		    		attempt++;
    		    		StatusController.addStatus("Try: "+attempt+"/"+maxRetries+", Script execution not completed. Next check is after 5 sec");
    		    		System.out.println("Script execution not completed. Next check is after 5 sec");
    		    	}
    		    	 try {
    					Thread.sleep(5000);
    				} catch (InterruptedException e) {
    					// TODO Auto-generated catch block
    					e.printStackTrace();
    				}
    		    	 }
    		    	if(attempt > maxRetries) {
    		    		StatusController.addStatus("Max retries completed.");
    		    	}
    		    	 if(executed) {
    		    		 if(uploadExtension(url, username, password, dataSource)) {
    		    			 if(setPersistenceLocale(url, username, password, dataSource, locale)){
    		    				 StatusController.addStatus("✅ Schema Initialization Completed Successfully");
    		    			 }
    		    		 }
    		    	 }
    			}
    		}
    	}
    	
    	
    	 long diff = System.currentTimeMillis()-startTime;
    	 StatusController.addStatus("Initialization completed in "+diff+" ms");
    	 System.out.println("Initialization completed in "+diff+" ms");
	
	}
	
	private boolean setPersistenceLocale(String url, String username, String password, String dataSource, String locale) {
        String apiUrl = (dataSource != null && !dataSource.isEmpty())
       		    ? url + "/persistencelocale?persistenceLocale="+locale+"&datasource=" + dataSource : url + "/persistencelocale?persistenceLocale="+locale;
        boolean isSuccess = false;
        try {
           HttpClient client = HttpClient.newBuilder()
                   .connectTimeout(CONNECT_TIMEOUT)
                   .build();

           HttpRequest request = HttpRequest.newBuilder()
                   .uri(URI.create(apiUrl))
                   .header("Accept", "*/*")
                   .header("Authorization", AppUtils.basicAuth(username, password))
                   .PUT(HttpRequest.BodyPublishers.noBody()) // No body for this request
                   .timeout(READ_TIMEOUT)
                   .build();

           HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

           // ✅ Check if status is SUCCESS in JSON response
           if (response.statusCode() == 200) {
               System.out.println("✅ Persistence locale updated successfully!");
               StatusController.addStatus("✅ Persistence locale updated successfully!");
               isSuccess = true ;
           } else {
               System.out.println("❌ Persistence locale Update failed. Response: " + response.body());
               StatusController.addStatus("❌ Persistence locale Update failed. Response: " + response.body());
               isSuccess = false ;
           }
           addToPdf(CurlUtil.toCurl(request), request.uri());
       } catch (Exception e) {
    	   isSuccess = false ;
           e.printStackTrace();
       }
        return isSuccess ;

   }
	
	public boolean getModelExtension(String url, String username, String password, String dataSource, MultipartFile model, MultipartFile data) {
		
		boolean isSuccess = false ;
		
    	String apiUrl = (dataSource != null && !dataSource.isEmpty())
    ? url + "/modelextensionfiles?dataSource=" + dataSource : url + "/modelextensionfiles";

        Path outputDir   = Paths.get(DEFAULT_OUTPUT_DIR);
        if(null == model || null == data || model.isEmpty() || data.isEmpty()) {
        try {
            Files.createDirectories(outputDir);

            // 1) Create client
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build();

            // 2) Build request with Basic Auth header and Accept: */*
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(apiUrl))
                    .header("Accept", "*/*")
                    .header("Authorization", AppUtils.basicAuth(username, password))
                    .timeout(READ_TIMEOUT)
                    .build();

            // 3) Send request and stream body
            HttpResponse<InputStream> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            int status = response.statusCode();
            if (status != 200) {
                throw new IOException("Unexpected HTTP status: " + status);
            }

            // 4) Decide a filename from Content-Disposition (if any)
            String zipFileName = AppUtils.getFileNameFromContentDisposition(response)
                    .orElse("model-extension-files.zip");

            // 5) Save response to a temp zip
            Path tempZip = Files.createTempFile("model-extensions-", "-" + zipFileName);
            try (InputStream is = response.body()) {
                Files.copy(is, tempZip, StandardCopyOption.REPLACE_EXISTING);
            }

            // 6) Extract safely
            AppUtils.extractZip(tempZip, outputDir);

            // 7) Cleanup
            Files.deleteIfExists(tempZip);

            System.out.println("✅ Downloaded and extracted ZIP to: " + outputDir.toAbsolutePath());
            StatusController.addStatus("✅ Downloaded and extracted ZIP to: " + outputDir.toAbsolutePath());
            AppUtils.renameExtensionFiles(outputDir);
            isSuccess = true;
            addToPdf(CurlUtil.toCurl(request), request.uri());

        } catch (Exception e) {
            isSuccess = false ;
        	System.err.println("❌ Failed: " + e.getMessage());
            StatusController.addStatus("❌ Failed: " + e.getMessage());
            e.printStackTrace();
           
        }
		} else {
			try {
				Files.createDirectories(outputDir);
				Path tempZip = Files.createTempFile("model-extensions-", ".zip");
				try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip))) {
				    // Add model file
				    zos.putNextEntry(new ZipEntry(model.getOriginalFilename())); // change name as needed
				    try (InputStream in = model.getInputStream()) {
				        in.transferTo(zos);
				    }
				    zos.closeEntry();
				    // Add data file
				    zos.putNextEntry(new ZipEntry(data.getOriginalFilename())); // change name as needed
				    try (InputStream in = data.getInputStream()) {
				        in.transferTo(zos);
				    }
				    zos.closeEntry();
				}
				AppUtils.extractZip(tempZip, outputDir);
				 Files.deleteIfExists(tempZip);
				 StatusController.addStatus("✅ Copied model and data extension to: " + outputDir.toAbsolutePath());
		            AppUtils.renameExtensionFiles(outputDir);
		            isSuccess = true;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
        
        return isSuccess;
    }
	
	private boolean  isExecuted(String url, String username, String password, String dataSource) {
    	String apiUrl = (dataSource != null && !dataSource.isEmpty())
    		    ? url + "/status?datasource=" + dataSource : url + "/status";
        boolean checkExecuted = false ;
        // 1) Create client
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();

        // 2) Build request with Basic Auth header and Accept: */*
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(apiUrl))
                .header("Accept", "*/*")
                .header("Authorization", AppUtils.basicAuth(username, password))
                .timeout(READ_TIMEOUT)
                .build();

        // 3) Send request and stream body
        try {
			HttpResponse<String> response = client.send(
			        request,
			        HttpResponse.BodyHandlers.ofString()
			);
			String respBody = response.body();
			System.out.println(respBody);
			checkExecuted = respBody.contains("SUCCESS");
			addToPdf(CurlUtil.toCurl(request), request.uri());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        if(checkExecuted) {
        	System.out.println("✅ Script execution completed");
        	StatusController.addStatus("✅ Script execution completed");
        } else {
        	System.out.println("Script execution not completed");
        	StatusController.addStatus("Script execution not completed");
        }
		return checkExecuted;
    	
    }
	
	private boolean executeCreateSchema(String url, String username, String password, String dataSource ) {
    	String apiUrl = (dataSource != null && !dataSource.isEmpty())
    			? url + "/execute?datasource=" + dataSource : url + "/execute";

        boolean isSuccess = false ;
    	String scriptPath    = DEFAULT_OUTPUT_SCRIPT;
        Path createScript = Paths.get(scriptPath);
        try {
        	AppUtils.requireReadableFile(createScript, "createSchema.sql");
			
			 MultipartBodyPublisher mp = new MultipartBodyPublisher();
             mp.addFilePart("sqlScriptFile", createScript, AppUtils.detectContentType(createScript));
           
             BodyPublisher body = mp.build();
             
             HttpRequest request = HttpRequest.newBuilder()
                     .uri(URI.create(apiUrl))
                     .header("Accept", "*/*")
                     .header("Authorization", AppUtils.basicAuth(username, password))
                     .header("Content-Type", mp.getContentType())
                     .timeout(READ_TIMEOUT)
                     .POST(body) // chunked streaming, no concat()
                     .build();
             
             HttpClient client = HttpClient.newBuilder()
                     .connectTimeout(CONNECT_TIMEOUT)
                     .build();

             // Send and stream response
             HttpResponse<InputStream> response = client.send(
                     request, HttpResponse.BodyHandlers.ofInputStream()
             );

             int status = response.statusCode();
             if (status < 200 || status >= 300) {
                 // Try to capture response body for diagnostics
                 String bodyPreview;
                 isSuccess = false ;
                 try (InputStream is = response.body()) {
                     bodyPreview = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                 } catch (Exception e) {
                     bodyPreview = "<unavailable>";
                 }
                 throw new IOException("Unexpected HTTP status: " + status + " Body: " + bodyPreview);
             }
             System.out.println("✅ SQL Script executed");
             StatusController.addStatus("✅ SQL Script executed");
             addToPdf(CurlUtil.toCurl(request), request.uri());
             isSuccess = true;
		} catch (Exception e) {
			isSuccess = true;
			e.printStackTrace();
		}
        return isSuccess;
    }
	
	private boolean prepareSchemaScript(String url, String username, String password, String dataSource) {
   	 Path modelPath   = Paths.get(DEFAULT_MODEL_PATH);
        Path dataPath    = Paths.get(DEFAULT_DATA_PATH);
        String outArg    = DEFAULT_OUTPUT_SCRIPT;
        String apiUrl = (dataSource != null && !dataSource.isEmpty())
       		 ? url + "/createschema?datasource=" + dataSource : url + "/createschema";
        boolean isSuccess = false;
        try {
            // Validate inputs
       	 AppUtils.requireReadableFile(modelPath, "extensionModelFile");
       	 AppUtils.requireReadableFile(dataPath,  "extensionDataFile");

            // Build multipart body (Java 11 friendly)
            MultipartBodyPublisher mp = new MultipartBodyPublisher();
            mp.addFilePart("extensionModelFile", modelPath, AppUtils.detectContentType(modelPath));
            mp.addFilePart("extensionDataFile",  dataPath,  AppUtils.detectContentType(dataPath));
            BodyPublisher body = mp.build();

            // HTTP client and request
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Accept", "*/*")
                    .header("Authorization", AppUtils.basicAuth(username, password))
                    .header("Content-Type", mp.getContentType())
                    .timeout(READ_TIMEOUT)
                    .POST(body) // chunked streaming, no concat()
                    .build();

            // Send and stream response
            HttpResponse<InputStream> response = client.send(
                    request, HttpResponse.BodyHandlers.ofInputStream()
            );

            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                // Try to capture response body for diagnostics
                String bodyPreview;
                try (InputStream is = response.body()) {
                    bodyPreview = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    bodyPreview = "<unavailable>";
                }
                throw new IOException("Unexpected HTTP status: " + status + " Body: " + bodyPreview);
            }


            Path outputPath = AppUtils.resolveOutputPath(outArg, null);
           // Files.createDirectories(outputPath.getParent());

            try (InputStream is = response.body();
                 OutputStream os = Files.newOutputStream(outputPath,
                         StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = is.read(buf)) != -1) {
                    os.write(buf, 0, r);
                }
            }

            System.out.println("✅ Schema SQL saved to: " + outputPath.toAbsolutePath());
            StatusController.addStatus("✅ Schema SQL saved to: " + outputPath.toAbsolutePath());
            addToPdf(CurlUtil.toCurl(request), request.uri());
            isSuccess = true;

        } catch (Exception e) {
        	isSuccess = false;
        	System.err.println("❌ Failed: " + e.getMessage());
            StatusController.addStatus("❌ Failed: " + e.getMessage());
            e.printStackTrace();
           
        }
        return isSuccess ;
   }
	
	private boolean uploadExtension(String url, String username, String password, String dataSource) {
	   	String apiUrl = (dataSource != null && !dataSource.isEmpty())
	   		 ? url + "/uploadextensionmodel?datasource=" + dataSource : url + "/uploadextensionmodel";
	        Path modelPath   = Paths.get(DEFAULT_MODEL_PATH);
	        Path dataPath    = Paths.get(DEFAULT_DATA_PATH);
	        boolean isSuccess = false ;

	        try {
	            // Validate inputs
	        	AppUtils.requireReadableFile(modelPath, "extensionModelFile");
	        	AppUtils.requireReadableFile(dataPath,  "extensionDataFile");

	            // Build multipart body (Java 11 friendly)
	            MultipartBodyPublisher mp = new MultipartBodyPublisher();
	            mp.addFilePart("extensionModelFile", modelPath, AppUtils.detectContentType(modelPath));
	            mp.addFilePart("extensionDataFile",  dataPath,  AppUtils.detectContentType(dataPath));
	            BodyPublisher body = mp.build();

	            // HTTP client and request
	            HttpClient client = HttpClient.newBuilder()
	                    .connectTimeout(CONNECT_TIMEOUT)
	                    .build();

	            HttpRequest request = HttpRequest.newBuilder()
	                    .uri(URI.create(apiUrl))
	                    .header("Accept", "*/*")
	                    .header("Authorization", AppUtils.basicAuth(username, password))
	                    .header("Content-Type", mp.getContentType())
	                    .timeout(READ_TIMEOUT)
	                    .POST(body) // chunked streaming, no concat()
	                    .build();

	            // Send and stream response
	            HttpResponse<InputStream> response = client.send(
	                    request, HttpResponse.BodyHandlers.ofInputStream()
	            );

	            int status = response.statusCode();
	            if (status < 200 || status >= 300) {
	                // Try to capture response body for diagnostics
	                String bodyPreview;
	                isSuccess = false ;
	                try (InputStream is = response.body()) {
	                    bodyPreview = new String(is.readAllBytes(), StandardCharsets.UTF_8);
	                } catch (Exception e) {
	                    bodyPreview = "<unavailable>";
	                }
	                throw new IOException("Unexpected HTTP status: " + status + " Body: " + bodyPreview);
	            }

	           System.out.println("✅ Uploaded model extension files" );
	           StatusController.addStatus("✅ Uploaded model extension files");
	           addToPdf(CurlUtil.toCurl(request), request.uri());
	           isSuccess = true ;

	        } catch (Exception e) {
	        	isSuccess = false ;
	            System.err.println("❌ Failed: " + e.getMessage());
	            StatusController.addStatus("❌ Failed: " + e.getMessage());
	            e.printStackTrace();
	            
	        }  
	        return isSuccess ;
	   }
	
	public void addToPdf(String curl, URI uri) {
		 StatusController.pdfMessage.add("Executed API: "+uri.getPath());
		 StatusController.pdfMessage.add(curl);

	}

}
