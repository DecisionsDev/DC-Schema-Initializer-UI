<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<head>
<link rel="shortcut icon" href="favicon.ico?" type="image/x-icon" />
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.10.5/font/bootstrap-icons.css">
    <title>Initialize DC Schema</title>
    <style>
        
body {
    font-family: 'Segoe UI', sans-serif;
    background-image: url('${pageContext.request.contextPath}/splash.jpeg');
    background-size: cover;
    background-position: center;
    background-repeat: no-repeat;
    background-attachment: fixed;
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
    margin: 0;
}

.container {
    background: rgba(255, 255, 255, 0.95); /* Slight transparency for background separation */
    padding: 25px 30px;
    border-radius: 12px;
    box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2);
    width: 500px;
    max-height: 80vh;
    overflow-y: auto;
}

input[type=text], input[type=password] {
    width: 100%;
    padding: 8px 12px;
    margin: 10px 0;
    border-radius: 6px;
    border: 1px solid #ccc;
    font-size: 14px;
}

input[type=submit] {
    width: 100%;
    background-color: #249bc6;
    color: white;
    border: none;
    padding: 10px;
    border-radius: 6px;
    cursor: pointer;
    font-size: 14px;
}

.floating-button {
    width: 50%;
    background-color: #249bc6;
    color: white;
    border: none;
    padding: 10px;
    border-radius: 6px;
    cursor: pointer;
    font-size: 14px;
}

.top-banner {
    
width: 500px;
    text-align: center;
    color: white;
    padding: 10px;
    font-size: 16px;
    margin-bottom: 20px;
}

.required:after {

content: " *";
color: red;
font-size: 12px;
}
      
    </style>
    <script>

const eventSource = new EventSource('/status-stream');

    eventSource.onmessage = function(event) {
    	const isSubmit = document.getElementById("isSubmitted") ;
    	if(isSubmit.value == 'yes') {
        const log = document.getElementById("statusLog");
        let msg = event.data ;
			log.innerHTML += msg + "<br/><br/>";
    	}
        
    };
    

    function handleSubmit() {
        document.getElementById("initForm").style.display = 'none';
        document.getElementById("isSubmitted").value = 'yes';
        return true;
      }
    
    function downloadPdf() {
        // Get the value of the model attribute
        var dataToExport = "${message}"; 
        
        // Redirect to the REST endpoint with the data as a query parameter or use a form submission/AJAX POST for large/sensitive data
        window.location.href = "/download-pdf?data=" + encodeURIComponent(dataToExport);
    }


</script>
</head>
<body>
<div class="top-banner">
<img alt="" src="${pageContext.request.contextPath}/login_IBM_logo_116.png">
    <h2>Initialize DC Schema</h2>
    </div>
<div class="container">
    <input type ="hidden" name="isSubmitted" id="isSubmitted">
    <c:if test="${empty isSubmitted}">
    <form action="initialize" method="post"  id="initForm" enctype="multipart/form-data" onsubmit="handleSubmit()">
        <label class="required">URL:</label>
        <input type="text" name="url" required="required" placeholder="http(s)://host:port" oninvalid="this.setCustomValidity('Valid url is mandatory')"
         oninput="this.setCustomValidity('')">
        <label class="required">Username:</label>
        <input type="text" name="username" required="required" oninvalid="this.setCustomValidity('Valid username is mandatory')"
         oninput="this.setCustomValidity('')">
        <label class="required">Password:</label>
        <input type="password" name="password" required="required" oninvalid="this.setCustomValidity('Valid Password is mandatory')"
         oninput="this.setCustomValidity('')">
        <label>Extension model file&nbsp;:</label>
        <input type="file" name="modelExtension"><br/><br/>
        <label>Extension data file&nbsp;&nbsp;&nbsp;&nbsp;:</label>
        <input type="file" name="dataExtension"><br/><br/>
        <label>Data Source:</label>
        <input type="text" name="dataSource" placeholder="datasource jndi name">
        <label>Locale:</label>
        <input type="text" name="locale"  placeholder="Ex:en_US, en_GB, ja_JP">
        <input type="submit" value="Initialize" >
    </form>
    </c:if>
    <c:if test="${not empty message}">
        <div class="message">${message}</div><br/>
        <form action="init" method="get" >
        <input type="submit" value="Back" class=floating-button>
       
        </form>
        <br/>
        <form action="download-pdf" method="get">
         <button type="submit" class="btn btn-success">
         <i class="bi bi-download"></i>
         Download API details as PDF
         </button>
        </form>
    </c:if>
    <div id="statusLog"></div>
</div>
</body>

</html>
