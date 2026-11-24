package com.renovar.canteiro.io.reporting.infrastructure;

import com.renovar.canteiro.io.reporting.application.CashFlowPdfGenerator;
import com.renovar.canteiro.io.reporting.application.CompareCashPaymentsUseCase.Comparison;
import com.renovar.canteiro.io.reporting.application.ReadCashDueUseCase.Bucket;
import com.renovar.canteiro.io.reporting.application.ReadCashDueUseCase;
import com.renovar.canteiro.io.reporting.application.ReadRealizedCashUseCase;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Component
public class PdfBoxCashFlowPdfGenerator implements CashFlowPdfGenerator {
    private static final float LEFT = 42;
    private static final float RIGHT = 553;
    private static final Color INK = new Color(23, 43, 40);
    private static final Color MUTED = new Color(68, 89, 85);
    private static final Color LINE = new Color(207, 220, 216);
    private static final Color PAPER = new Color(240, 247, 244);
    private static final Color GREEN = new Color(16, 107, 80);
    private static final Color BLUE = new Color(40, 91, 139);
    private static final Color ORANGE = new Color(168, 73, 29);
    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    @Override
    public void write(Data data, OutputStream output) throws IOException {
        try (var pdf = new PDDocument(IOUtils.createTempFileOnlyStreamCache())) {
            boolean hasComparison = data.comparison() != null;
            try (var page = newPage(pdf)) {
                header(page, data, "Caixa e vencimentos", 1, hasComparison ? 2 : 1);
                realized(page, data.realized());
                due(page, data);
                if (!hasComparison) {
                    label(page, LEFT, 60, "Comparação de pagamentos indisponível por permissão.");
                }
            }
            if (hasComparison) {
                try (var page = newPage(pdf)) {
                    header(page, data, "Comparação de pagamentos", 2, 2);
                    comparison(page, data);
                }
            }
            pdf.save(output);
        }
    }

    private PDPageContentStream newPage(PDDocument pdf) throws IOException {
        var page = new PDPage(PDRectangle.A4);
        pdf.addPage(page);
        return new PDPageContentStream(pdf, page);
    }

    private void header(PDPageContentStream page, Data data, String section, int index, int total) throws IOException {
        fill(page, 0, 834, PDRectangle.A4.getWidth(), 8, GREEN);
        text(page, LEFT, 797, 12, BOLD, GREEN, "Canteiro.io");
        right(page, RIGHT, 797, 10, BOLD, GREEN, data.currency().name());
        text(page, LEFT, 765, 25, BOLD, INK, "Relatório financeiro");
        var company = safeName(data.companyName());
        text(page, LEFT, 741, 11, BOLD, INK, fit(BOLD, 11, "Empresa  /  " + company, RIGHT - LEFT));
        if (data.workName() != null) {
            text(page, LEFT, 724, 10, REGULAR, INK,
                    fit(REGULAR, 10, "Obra  /  " + safeName(data.workName()), RIGHT - LEFT));
        }
        label(page, LEFT, 703, "Período  " + DATE.format(data.from()) + " a " + DATE.format(data.to()));
        right(page, RIGHT, 703, 9, REGULAR, MUTED, "Data-base  " + DATE.format(data.asOf()) + " UTC");
        rule(page, LEFT, 689, RIGHT, 689);
        text(page, LEFT, 667, 16, BOLD, INK, section);
        rule(page, LEFT, 43, RIGHT, 43);
        label(page, LEFT, 28, "Canteiro.io  /  Caixa registrado");
        right(page, RIGHT, 28, 9, REGULAR, MUTED, "Página " + index + " de " + total);
    }

    private void realized(PDPageContentStream page, ReadRealizedCashUseCase.CurrencyPosition cash) throws IOException {
        label(page, LEFT, 649, "Realizado por data efetiva da baixa ou do estorno.");
        metric(page, 42, "Recebimentos", cash.receipts().amount(), coverage(cash.receipts()));
        metric(page, 214, "Pagamentos", cash.payments().amount(), coverage(cash.payments()));
        metric(page, 386, "Variação líquida", cash.netMovement(),
                cash.netMovement() == null ? "Exige acesso às duas fontes" : "Recebimentos menos pagamentos");
        bars(page, 548, List.of(new Bar("Recebimentos", cash.receipts().amount(), GREEN),
                new Bar("Pagamentos", cash.payments().amount(), BLUE)));
        text(page, LEFT, 482, 9, REGULAR, INK, "Pagamentos de obras");
        right(page, 300, 482, 9, BOLD, INK, amount(cash.workPayments().amount()));
        if (cash.companyPayments() != null) {
            text(page, 321, 482, 9, REGULAR, INK, "Pagamentos gerais");
            right(page, RIGHT, 482, 9, BOLD, INK, amount(cash.companyPayments().amount()));
        }
        rule(page, LEFT, 468, RIGHT, 468);
    }

    private void metric(PDPageContentStream page, float x, String title, BigDecimal value, String note) throws IOException {
        fill(page, x, 568, 167, 66, PAPER);
        label(page, x + 10, 617, title);
        text(page, x + 10, 594, fontSize(BOLD, 17, amount(value), 147), BOLD, INK, amount(value));
        text(page, x + 10, 578, 8, REGULAR, MUTED, note);
    }

    private String coverage(ReadRealizedCashUseCase.Component value) {
        return value.entryCount() == null ? "Sem permissão de exportação" : value.entryCount() + " movimentos registrados";
    }

    private void due(PDPageContentStream page, Data data) throws IOException {
        text(page, LEFT, 447, 14, BOLD, INK, "Saldos por vencimento");
        label(page, LEFT, 429, "Vencidos antes da data-base. A vencer até " + DATE.format(data.dueThrough()) + " (inclusive).");
        fill(page, LEFT, 398, RIGHT - LEFT, 20, PAPER);
        text(page, 50, 405, 9, BOLD, INK, "Posição");
        right(page, 351, 405, 9, BOLD, INK, "Vencidos");
        right(page, 545, 405, 9, BOLD, INK, "A vencer");
        var due = data.due();
        dueRow(page, 383, "A receber", due.receivables());
        dueRow(page, 344, "A pagar", due.payables());
        dueRow(page, 305, "A pagar em obras", due.workPayables());
        if (due.companyPayables() != null) {
            dueRow(page, 266, "A pagar da empresa", due.companyPayables());
        }
        pie(page, due.payables());
    }

    private void dueRow(PDPageContentStream page, float y, String title, ReadCashDueUseCase.Component position) throws IOException {
        text(page, 50, y, 10, BOLD, INK, title);
        bucket(page, 351, y, position.overdue());
        bucket(page, 545, y, position.upcoming());
        rule(page, LEFT, y - 22, RIGHT, y - 22);
    }

    private void bucket(PDPageContentStream page, float x, float y, Bucket value) throws IOException {
        right(page, x, y, 10, BOLD, INK, amount(value.amount()));
        String note = switch (value.coverage()) {
            case NO_PERMISSION -> "Sem permissão de exportação";
            case UNAVAILABLE -> value.unavailableTitleCount() + " títulos sem saldo apurado";
            case PARTIAL -> value.titleCount() + " apurados / " + value.unavailableTitleCount() + " indisponíveis";
            case OFFICIAL_RESIDUALS -> value.titleCount() + " saldos oficiais";
        };
        right(page, x, y - 13, 8, REGULAR, MUTED, note);
    }

    private void comparison(PDPageContentStream page, Data data) throws IOException {
        var previous = data.previousPeriod();
        label(page, LEFT, 646, "Período anterior  " + DATE.format(previous.from()) + " a " + DATE.format(previous.to()));
        label(page, LEFT, 629, "Pagamentos líquidos de estornos por data efetiva. Os dois intervalos têm a mesma duração.");
        var position = data.comparison();
        fill(page, LEFT, 588, RIGHT - LEFT, 22, PAPER);
        text(page, 50, 596, 9, BOLD, INK, "Escopo");
        right(page, 250, 596, 9, BOLD, INK, "Atual");
        right(page, 355, 596, 9, BOLD, INK, "Anterior");
        right(page, 460, 596, 9, BOLD, INK, "Diferença");
        right(page, 545, 596, 9, BOLD, INK, "Variação");
        comparisonRow(page, 570, "Pagamentos totais", position.payments());
        comparisonRow(page, 514, "Pagamentos de obras", position.workPayments());
        if (position.companyPayments() != null) {
            comparisonRow(page, 458, "Pagamentos gerais", position.companyPayments());
        }
        text(page, LEFT, 375, 13, BOLD, INK, "Pagamentos totais por período");
        bars(page, 345, List.of(new Bar("Período atual", chartAmount(position.payments().current()), BLUE),
                new Bar("Período anterior", chartAmount(position.payments().previous()), GREEN)));
        label(page, LEFT, 270, "A cobertura reflete os movimentos registrados em cada período.");
    }

    private BigDecimal chartAmount(com.renovar.canteiro.io.reporting.application.CompareCashPaymentsUseCase.PeriodTotal total) {
        return total.entryCount() == 0 ? null : total.amount();
    }

    private void comparisonRow(PDPageContentStream page, float y, String title, Comparison value) throws IOException {
        text(page, 50, y, 10, BOLD, INK, title);
        tableValue(page, 250, y, amount(value.current().amount()), 100);
        tableValue(page, 355, y, amount(value.previous().amount()), 100);
        tableValue(page, 460, y, amount(value.differenceAmount()), 100);
        tableValue(page, 545, y, value.percentageChange() == null ? "Indisponível" : amount(value.percentageChange()) + "%", 80);
        right(page, 250, y - 14, 8, REGULAR, MUTED, value.current().entryCount() + " movimentos");
        right(page, 355, y - 14, 8, REGULAR, MUTED, value.previous().entryCount() + " movimentos");
        String reason = switch (value.status()) {
            case INSUFFICIENT_RECORDS -> "Período sem movimentos registrados";
            case NON_POSITIVE_BASE -> "Base anterior não positiva";
            case RECORDED_MOVEMENTS -> "";
        };
        if (!reason.isEmpty()) label(page, 50, y - 30, reason);
        rule(page, LEFT, y - 39, RIGHT, y - 39);
    }

    private void tableValue(PDPageContentStream page, float x, float y, String value, float width) throws IOException {
        right(page, x, y, fontSize(BOLD, 10, value, width), BOLD, INK, value);
    }

    private void bars(PDPageContentStream page, float y, List<Bar> bars) throws IOException {
        var amounts = bars.stream().map(Bar::amount).filter(Objects::nonNull).toList();
        var min = amounts.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO).min(BigDecimal.ZERO);
        var max = amounts.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO).max(BigDecimal.ZERO);
        var range = max.subtract(min);
        float chartLeft = 184;
        float chartWidth = 277;
        float zero = range.signum() == 0 ? chartLeft
                : chartLeft + fraction(min.negate(), range) * chartWidth;
        if (range.signum() != 0) {
            rule(page, zero, y + 7, zero, y - 32);
            text(page, zero - 2, y - 41, 7, REGULAR, MUTED, "0");
        }
        for (var bar : bars) {
            label(page, LEFT, y, bar.title());
            right(page, RIGHT, y, 9, BOLD, INK, amount(bar.amount()));
            if (range.signum() != 0 && bar.amount() != null && bar.amount().signum() != 0) {
                float width = fraction(bar.amount(), range) * chartWidth;
                fill(page, zero + Math.min(width, 0), y - 3, Math.abs(width), 10, bar.color());
            }
            y -= 23;
        }
    }

    private float fraction(BigDecimal value, BigDecimal total) {
        return value.divide(total, MathContext.DECIMAL64).floatValue();
    }

    private void pie(PDPageContentStream page, ReadCashDueUseCase.Component position) throws IOException {
        var overdue = position.overdue().amount();
        var upcoming = position.upcoming().amount();
        if (overdue == null || upcoming == null || overdue.add(upcoming).signum() <= 0) return;
        var total = overdue.add(upcoming);
        double full = 2 * Math.PI;
        double split = full * overdue.divide(total, MathContext.DECIMAL64).doubleValue();
        text(page, LEFT, 221, 11, BOLD, INK, "Distribuição dos saldos apurados a pagar");
        sector(page, 0, split, ORANGE);
        sector(page, split, full, BLUE);
        legend(page, 165, ORANGE, "Vencidos", overdue, total);
        legend(page, 140, BLUE, "A vencer", upcoming, total);
        if (position.overdue().coverage() == ReadCashDueUseCase.Coverage.PARTIAL
                || position.upcoming().coverage() == ReadCashDueUseCase.Coverage.PARTIAL) {
            label(page, 212, 112, "Participação calculada sobre os saldos disponíveis.");
        }
    }

    private void sector(PDPageContentStream page, double start, double end, Color color) throws IOException {
        if (end <= start) return;
        page.setNonStrokingColor(color);
        page.moveTo(112, 148);
        for (int step = 0; step <= 96; step++) {
            double angle = start + (end - start) * step / 96;
            page.lineTo(112 + (float) (49 * Math.sin(angle)), 148 + (float) (49 * Math.cos(angle)));
        }
        page.closePath();
        page.fill();
    }

    private void legend(PDPageContentStream page, float y, Color color, String title, BigDecimal value, BigDecimal total)
            throws IOException {
        fill(page, 212, y - 2, 9, 9, color);
        text(page, 229, y, 9, REGULAR, INK, title);
        right(page, 450, y, 10, BOLD, INK, amount(value));
        String percent = value.movePointRight(2).divide(total, 1, RoundingMode.HALF_UP).toPlainString() + "%";
        right(page, RIGHT, y, 10, BOLD, INK, percent);
    }

    private String safeName(String name) throws IOException {
        if (name == null) return "";
        var result = new StringBuilder();
        for (int codePoint : name.codePoints().toArray()) {
            String character = new String(Character.toChars(codePoint));
            try {
                REGULAR.encode(character);
                result.append(character);
            } catch (IllegalArgumentException unsupported) {
                result.append(' ');
            }
        }
        return result.toString().strip();
    }

    private String fit(PDFont font, float size, String value, float width) throws IOException {
        if (width(font, size, value) <= width) return value;
        int end = value.length();
        while (end > 0 && width(font, size, value.substring(0, end) + "...") > width) end--;
        return value.substring(0, end).stripTrailing() + "...";
    }

    private String amount(BigDecimal value) {
        return value == null ? "Indisponível" : value.setScale(2).toPlainString();
    }

    private float fontSize(PDFont font, float nominal, String value, float available) throws IOException {
        return Math.min(nominal, nominal * available / width(font, nominal, value));
    }

    private float width(PDFont font, float size, String value) throws IOException {
        return font.getStringWidth(value) / 1000 * size;
    }

    private void label(PDPageContentStream page, float x, float y, String value) throws IOException {
        text(page, x, y, 9, REGULAR, MUTED, value);
    }

    private void right(PDPageContentStream page, float x, float y, float size, PDFont font, Color color, String value)
            throws IOException {
        text(page, x - width(font, size, value), y, size, font, color, value);
    }

    private void text(PDPageContentStream page, float x, float y, float size, PDFont font, Color color, String value)
            throws IOException {
        page.beginText();
        page.setNonStrokingColor(color);
        page.setFont(font, size);
        page.newLineAtOffset(x, y);
        page.showText(value);
        page.endText();
    }

    private void fill(PDPageContentStream page, float x, float y, float width, float height, Color color) throws IOException {
        page.setNonStrokingColor(color);
        page.addRect(x, y, width, height);
        page.fill();
    }

    private void rule(PDPageContentStream page, float x1, float y1, float x2, float y2) throws IOException {
        page.setStrokingColor(LINE);
        page.setLineWidth(0.8f);
        page.moveTo(x1, y1);
        page.lineTo(x2, y2);
        page.stroke();
    }

    private record Bar(String title, BigDecimal amount, Color color) { }
}
