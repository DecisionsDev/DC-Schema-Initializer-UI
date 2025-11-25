package com.ibm.initialize.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ibm.initialize.service.SchemaInitializerService;
import com.ibm.initialize.utils.AppUtils;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

@Controller
public class SchemaInitializerController {
	
	@Autowired
    private SchemaInitializerService initService;
	
	 @GetMapping("/")
	    public String showInitPage() {
		 StatusController.statusMessage.clear();
	        return "init";
	    }
	    @PostMapping("/initialize")
	    public String initialize(
	            @RequestParam(required = true) String url,
	            @RequestParam(required = true) String username,
	            @RequestParam(required = true) String password,
	            @RequestParam(required = false) MultipartFile modelExtension,
	            @RequestParam(required = false) MultipartFile dataExtension,
	            @RequestParam(required = false) String dataSource,
	            @RequestParam(required = false) String locale,
	            RedirectAttributes model) {
	    	try {
	    		
	    	StatusController.statusMessage.clear();
	    	StatusController.pdfMessage.clear();
	    	//StatusController.emitters.clear();
	    	
	    	if(validateInputReq(url, username, password, dataSource, locale, modelExtension, dataExtension)) {
	        initService.initializeSchema(url, username, password, dataSource, locale, modelExtension, dataExtension);
	    	}
	        String response = StatusController.statusMessage.stream().collect(Collectors.joining("<br/><br/>"));
	        
	       model.addFlashAttribute("message", response);
	       model.addFlashAttribute("isSubmitted","yes");
	        return "redirect:/";
	    	} catch (Exception e) {
	    		 model.addFlashAttribute("message", e.getMessage());
	    		 return "redirect:/" ;
	    	}
	    }
	    
	    @GetMapping("/init")
	    public String showInitPageWithModel() {
	    	StatusController.statusMessage.clear();
	    	 return "redirect:/" ;
	    }
	    
	    @GetMapping("/download-pdf")
	    public ResponseEntity<byte[]> downloadPdf() throws DocumentException, IOException {
	        
	        // 1. Generate the PDF as a byte array
	        ByteArrayOutputStream baos = new ByteArrayOutputStream();
	        Document document = new Document(PageSize.A4);
	        PdfWriter.getInstance(document, baos);
	        Font headingFont = new Font(Font.HELVETICA, 18, Font.BOLD);
	        Font subHeadingFont = new Font(Font.HELVETICA, 14, Font.BOLD);
	        Font paraFont = new Font(Font.HELVETICA, 10, Font.ITALIC);

	        document.open();
	        Paragraph heading = new Paragraph("Initialize DC Schema:",headingFont);
	        heading.setAlignment(Element.ALIGN_CENTER);
	        document.add(heading);
	        for(String data : StatusController.pdfMessage) {
	        	if(data.contains("Executed API:")) {
	        		document.add(new Paragraph(data, subHeadingFont));
	        	} else {
	        document.add(new Paragraph(data, paraFont));
	        	}
	        }
	        document.close();
	        
	        byte[] pdfBytes = baos.toByteArray();

	        // 2. Set HTTP headers for download
	        HttpHeaders headers = new HttpHeaders();
	        headers.setContentType(MediaType.APPLICATION_PDF);
	        headers.setContentDispositionFormData("attachment", "DC_Initialize_Schema_API_details.pdf"); // Prompts browser to download
	        headers.setContentLength(pdfBytes.length);

	        // 3. Return the response entity
	        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
	    }
	    
	    private boolean validateInputReq(String url, String username, String password, String dataSource, String locale, MultipartFile model, MultipartFile data) {
	    	
	    	if(!AppUtils.isValidUrl(url)) {
	    		StatusController.addStatus("❌ URL is Invalid. Must be in the format http(s)://host:port");
	    		return false ;
	    	}
	    	if(null == username || username.isEmpty()) {
	    		StatusController.addStatus("❌ Username cannot be empty");
	    		return false ;
	    	}
	    	
	    	if(null == password || password.isEmpty()) {
	    		StatusController.addStatus("❌ Password cannot be empty");
	    		return false ;
	    	}
	    	if(null != model && !model.isEmpty()) {
	    		System.out.println("Model file passed: "+model.getName());
	    		if(null == data || data.isEmpty()) {
	    			StatusController.addStatus("❌ Extension Data file is not attached.");
	    			return false ;
	    		}
	    		if(!model.getOriginalFilename().endsWith(".brmx")) {
	    			StatusController.addStatus("❌ Extension model file is not proper.");
	    			return false ;
	    		}
	    	}
	    	if(null != data && !data.isEmpty()) {
	    		System.out.println("Data file passed: "+data.getName());
	    		if(null == model || model.isEmpty()) {
	    			StatusController.addStatus("❌ Extension model file is not attached.");
	    			return false ;
	    		}
	    		if(!data.getOriginalFilename().endsWith(".brdx")) {
	    			StatusController.addStatus("❌ Extension data file is not proper.");
	    			return false ;
	    		}
	    	}
	    	return true ;
	    }
	
}
