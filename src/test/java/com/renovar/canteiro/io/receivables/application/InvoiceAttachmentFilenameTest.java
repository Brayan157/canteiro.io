package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceAttachmentFilenameTest {
    @Test void removesPathsHeaderControlsAndInvalidCharactersAndFixesExtension() {
        assertEquals("nota-acao-01.pdf", InvoiceAttachmentFilename.download("C:\\pasta\\Nota Ação 01.exe", InvoiceAttachmentType.PDF));
        assertEquals("nota-set-cookie-erro.xml", InvoiceAttachmentFilename.download("../nota\r\nSet-Cookie: erro.pdf", InvoiceAttachmentType.XML));
        assertEquals("anexo-nf.pdf", InvoiceAttachmentFilename.download("NUL.pdf", InvoiceAttachmentType.PDF));
        assertEquals("anexo-nf.xml", InvoiceAttachmentFilename.download(null, InvoiceAttachmentType.XML));
        assertEquals("anexo-nf.pdf", InvoiceAttachmentFilename.download("???.pdf", InvoiceAttachmentType.PDF));
        assertTrue(InvoiceAttachmentFilename.download("a".repeat(255) + ".xml", InvoiceAttachmentType.XML).length() <= 124);
        assertEquals("Nota Ação.pdf", InvoiceAttachmentFilename.display("C:\\pasta\\Nota Ação.pdf", InvoiceAttachmentType.PDF));
        assertEquals("Nota--Set-Cookie-erro.xml", InvoiceAttachmentFilename.display("../Nota\r\nSet-Cookie:erro.xml", InvoiceAttachmentType.XML));
    }
}
