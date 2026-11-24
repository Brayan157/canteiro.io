package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

class InvoiceAttachmentContentValidatorTest {
    @Test
    void acceptsRealPdfAndWellFormedXml() throws Exception {
        assertDoesNotThrow(() -> InvoiceAttachmentContentValidator.validate(InvoiceAttachmentType.PDF, pdf(false, true)));
        assertDoesNotThrow(() -> xml("<?xml version=\"1.0\"?><nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\"><NFe/></nfeProc>"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a PDF", "%PDF-1.7\nnot a PDF object", "<html>invalid</html>"})
    void rejectsFilesDisguisedAsPdf(String content) {
        assertThrows(IllegalArgumentException.class, () -> InvoiceAttachmentContentValidator.validate(
                InvoiceAttachmentType.PDF, content.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsEncryptedOrEmptyPdf() throws Exception {
        byte[] encrypted = pdf(true, true);
        byte[] empty = pdf(false, false);
        assertThrows(IllegalArgumentException.class, () -> InvoiceAttachmentContentValidator.validate(InvoiceAttachmentType.PDF, encrypted));
        assertThrows(IllegalArgumentException.class, () -> InvoiceAttachmentContentValidator.validate(InvoiceAttachmentType.PDF, empty));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<root><unclosed></root>",
            "<!DOCTYPE root [<!ENTITY secret SYSTEM 'file:///private-test-path'>]><root>&secret;</root>",
            "<!DOCTYPE root SYSTEM 'https://invalid.example.test/external.dtd'><root/>",
            "<!DOCTYPE root [<!ENTITY a 'many'><!ENTITY b '&a;&a;'>]><root>&b;</root>",
            "<root xmlns:xi='http://www.w3.org/2001/XInclude'><xi:include href='file:///private-test-path'/></root>"
    })
    void rejectsMalformedXmlDoctypeEntitiesAndXincludeWithoutExposingPayload(String content) {
        var exception = assertThrows(IllegalArgumentException.class, () -> xml(content));
        assertFalse(exception.getMessage().contains("private-test-path"));
        assertFalse(exception.getMessage().contains("external.dtd"));
        org.junit.jupiter.api.Assertions.assertNull(exception.getCause());
    }

    @Test
    void rejectsExcessiveXmlDepthElementsAndAttributes() {
        assertThrows(IllegalArgumentException.class, () -> xml("<a>".repeat(129) + "</a>".repeat(129)));
        assertThrows(IllegalArgumentException.class, () -> xml("<root>" + "<a/>".repeat(100_001) + "</root>"));
        String attributes = java.util.stream.IntStream.range(0, 101).mapToObj(i -> " a" + i + "='x'")
                .collect(java.util.stream.Collectors.joining());
        assertThrows(IllegalArgumentException.class, () -> xml("<root" + attributes + "/>"));
    }

    private void xml(String content) {
        InvoiceAttachmentContentValidator.validate(InvoiceAttachmentType.XML, content.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] pdf(boolean encrypted, boolean page) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (page) document.addPage(new PDPage());
            if (encrypted) document.protect(new StandardProtectionPolicy("owner-test", "user-test", new AccessPermission()));
            document.save(output);
            return output.toByteArray();
        }
    }
}
