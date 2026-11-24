package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

final class InvoiceAttachmentFilename {
    private static final Set<String> RESERVED = Set.of("con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");
    private InvoiceAttachmentFilename() { }

    static String display(String original, InvoiceAttachmentType type) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}:*?\"<>|]", "-")
                .replaceAll("^\\.+|[. ]+$", "").trim();
        return name.isBlank() ? download(null, type) : name;
    }

    static String download(String original, InvoiceAttachmentType type) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        int extension = name.lastIndexOf('.');
        if (extension >= 0) name = name.substring(0, extension);
        name = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (name.length() > 120) name = name.substring(0, 120).replaceFirst("-+$", "");
        if (name.isBlank() || RESERVED.contains(name)) name = "anexo-nf";
        return name + (type == InvoiceAttachmentType.PDF ? ".pdf" : ".xml");
    }
}
