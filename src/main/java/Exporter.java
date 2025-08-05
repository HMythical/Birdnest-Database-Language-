import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import com.google.api.services.docs.v1.Docs;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.util.store.FileDataStoreFactory;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;

public class Exporter {
    private static final String APPLICATION_NAME = "BirdNest Database Language";
    private static final java.io.File CREDENTIALS_FOLDER = new java.io.File(System.getProperty("user.home"), ".credentials/bdl");
    private static final String GOOGLE_CREDENTIALS_FILE = "/google-credentials.json";

    private enum FileType {
        PDF, WORD, EXCEL, GDOC, GSHEET
    }

    // Get Google credentials
    private Credential getCredentials() throws IOException, GeneralSecurityException {
        // Load client secrets
        InputStream in = Exporter.class.getResourceAsStream(GOOGLE_CREDENTIALS_FILE);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + GOOGLE_CREDENTIALS_FILE);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
            GsonFactory.getDefaultInstance(), new InputStreamReader(in));

        // Build flow and trigger user authorization request
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            clientSecrets,
            Arrays.asList("https://www.googleapis.com/auth/documents", "https://www.googleapis.com/auth/spreadsheets")
        )
        .setDataStoreFactory(new FileDataStoreFactory(CREDENTIALS_FOLDER))
        .setAccessType("offline")
        .build();

        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver())
            .authorize("user");
    }

    // Export a nest to various file formats
    public void exportNest(Nest nest, String destination, String fileType) throws IOException, DocumentException {
        FileType type = FileType.valueOf(fileType.toUpperCase());
        switch (type) {
            case PDF:
                exportToPDF(nest, destination);
                break;
            case EXCEL:
                exportToExcel(nest, destination);
                break;
            case WORD:
                exportToWord(nest, destination);
                break;
            case GDOC:
                exportToGoogleDoc(nest, destination);
                break;
            case GSHEET:
                exportToGoogleSheet(nest, destination);
                break;
            default:
                throw new IllegalArgumentException("Unsupported file type: " + fileType);
        }
    }

    // Export to PDF using iText
    private void exportToPDF(Nest nest, String destination) throws IOException, DocumentException {
        com.itextpdf.text.Document document = new com.itextpdf.text.Document();
        PdfWriter.getInstance(document, new FileOutputStream(destination));
        document.open();

        // Add nest metadata
        document.add(new com.itextpdf.text.Paragraph("Nest Name: " + nest.getName()));
        document.add(new com.itextpdf.text.Paragraph("Created: " + nest.getCreationDate()));

        // Create table for eggs
        PdfPTable table = new PdfPTable(nest.getEggs().size());
        // Add headers
        for (Egg egg : nest.getEggs()) {
            table.addCell(egg.getName());
        }

        // Add data rows
        for (Egg egg : nest.getEggs()) {
            table.addCell(String.valueOf(egg.getValue()));
        }

        document.add(table);
        document.close();
    }

    // Export to Excel using Apache POI
    private void exportToExcel(Nest nest, String destination) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet(nest.getName());

        // Create header row
        Row headerRow = sheet.createRow(0);
        java.util.List<Egg> eggs = nest.getEggs();
        for (int i = 0; i < eggs.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(eggs.get(i).getName());
        }

        // Add data rows
        int rowNum = 1;
        for (Egg egg : nest.getEggs()) {
            Row row = sheet.createRow(rowNum++);
            Cell cell = row.createCell(0);
            cell.setCellValue(String.valueOf(egg.getValue()));
        }

        // Auto-size columns
        for (int i = 0; i < eggs.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        try (FileOutputStream out = new FileOutputStream(destination)) {
            workbook.write(out);
        }
        workbook.close();
    }

    // Export to Word using Apache POI
    private void exportToWord(Nest nest, String destination) throws IOException {
        XWPFDocument document = new XWPFDocument();

        // Add title
        XWPFParagraph title = document.createParagraph();
        XWPFRun titleRun = title.createRun();
        titleRun.setText("Nest: " + nest.getName());
        titleRun.setBold(true);

        // Create table
        XWPFTable table = document.createTable();
        // Add headers
        XWPFTableRow headerRow = table.getRow(0);
        for (int i = 0; i < nest.getEggs().size(); i++) {
            if (i == 0) {
                headerRow.getCell(0).setText(nest.getEggs().get(i).getName());
            } else {
                headerRow.addNewTableCell().setText(nest.getEggs().get(i).getName());
            }
        }

        // Add data rows
        // Implementation depends on how data is stored in eggs

        try (FileOutputStream out = new FileOutputStream(destination)) {
            document.write(out);
        }
        document.close();
    }

    // Export to Google Docs
    private void exportToGoogleDoc(Nest nest, String destination) throws IOException {
        try {
            Docs docsService = new Docs.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                getCredentials())
                .setApplicationName(APPLICATION_NAME)
                .build();

            // Create document
            com.google.api.services.docs.v1.model.Document doc = new com.google.api.services.docs.v1.model.Document()
                .setTitle("Nest: " + nest.getName());

            // Create the document first
            doc = docsService.documents().create(doc).execute();
            String documentId = doc.getDocumentId();

            // Prepare content
            java.util.List<com.google.api.services.docs.v1.model.Request> requests = new ArrayList<>();

            // Add title
            requests.add(new com.google.api.services.docs.v1.model.Request()
                .setInsertText(new com.google.api.services.docs.v1.model.InsertTextRequest()
                    .setText("Nest: " + nest.getName() + "\n")
                    .setLocation(new com.google.api.services.docs.v1.model.Location().setIndex(1))));

            // Add content for each egg
            int currentIndex = nest.getName().length() + 7;
            for (Egg egg : nest.getEggs()) {
                requests.add(new com.google.api.services.docs.v1.model.Request()
                    .setInsertText(new com.google.api.services.docs.v1.model.InsertTextRequest()
                        .setText(egg.getName() + ": " + egg.getValue() + "\n")
                        .setLocation(new com.google.api.services.docs.v1.model.Location()
                            .setIndex(currentIndex))));
                currentIndex += egg.getName().length() + egg.getValue().toString().length() + 3;
            }

            // Execute the requests
            docsService.documents()
                .batchUpdate(documentId, new com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest()
                    .setRequests(requests))
                .execute();

            // Save document ID to destination if needed
            if (destination != null && !destination.isEmpty()) {
                try (FileWriter writer = new FileWriter(destination)) {
                    writer.write("Document ID: " + documentId);
                }
            }

        } catch (GeneralSecurityException e) {
            throw new IOException("Security error while exporting to Google Docs", e);
        }
    }

    // Export to Google Sheets
    private void exportToGoogleSheet(Nest nest, String destination) throws IOException {
        try {
            Sheets sheetsService = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                getCredentials())
                .setApplicationName(APPLICATION_NAME)
                .build();

            // Create new spreadsheet
            Spreadsheet spreadsheet = new Spreadsheet()
                .setProperties(new SpreadsheetProperties()
                    .setTitle("Nest: " + nest.getName()));
            spreadsheet = sheetsService.spreadsheets().create(spreadsheet)
                .setFields("spreadsheetId")
                .execute();

            // Prepare values
            java.util.List<java.util.List<Object>> values = new ArrayList<>();

            // Add headers
            java.util.List<Object> headers = new ArrayList<>();
            for (Egg egg : nest.getEggs()) {
                headers.add(egg.getName());
            }
            values.add(headers);

            // Add data
            java.util.List<Object> dataRow = new ArrayList<>();
            for (Egg egg : nest.getEggs()) {
                dataRow.add(egg.getValue());
            }
            values.add(dataRow);

            ValueRange body = new ValueRange()
                .setValues(values);

            sheetsService.spreadsheets().values()
                .update(spreadsheet.getSpreadsheetId(), "A1", body)
                .setValueInputOption("RAW")
                .execute();

            // Save spreadsheet ID to destination if needed
            if (destination != null && !destination.isEmpty()) {
                try (FileWriter writer = new FileWriter(destination)) {
                    writer.write("Spreadsheet ID: " + spreadsheet.getSpreadsheetId());
                }
            }

        } catch (GeneralSecurityException e) {
            throw new IOException("Security error while exporting to Google Sheets", e);
        }
    }

    // Method to handle recursive exports for sub-nests
    public void exportNestRecursively(Nest nest, String destination, String fileType) throws IOException, DocumentException {
        exportNest(nest, destination, fileType);

        // Export each sub-nest
        for (Nest subNest : nest.getSubNests()) {
            String subDestination = destination.substring(0, destination.lastIndexOf('.'))
                + "_" + subNest.getName()
                + destination.substring(destination.lastIndexOf('.'));
            exportNestRecursively(subNest, subDestination, fileType);
        }
    }

    // Export entire tree
    public void exportTree(Tree tree, String destination, String fileType) throws IOException, DocumentException {
        // Create directory for tree export
        String dirPath = destination.substring(0, destination.lastIndexOf(File.separator));
        if (!new File(dirPath).mkdirs() && !new File(dirPath).exists()) {
            throw new IOException("Failed to create directory: " + dirPath);
        }

        // Export each nest in the tree
        for (Nest nest : tree.getNests()) {
            String nestDestination = dirPath + File.separator + nest.getName() + "." + fileType.toLowerCase();
            exportNestRecursively(nest, nestDestination, fileType);
        }

        // Create index/summary file
        createTreeSummary(tree, destination, fileType);
    }

    private void createTreeSummary(Tree tree, String destination, String fileType) {
        // Remove throws clause since exceptions aren't thrown
        // Implementation would go here
    }
}
