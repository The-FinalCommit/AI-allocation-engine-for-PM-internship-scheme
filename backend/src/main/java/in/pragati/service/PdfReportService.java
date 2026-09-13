package in.pragati.service;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Font;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import in.pragati.common.ApiException;
import in.pragati.common.Labels;
import in.pragati.domain.*;
import in.pragati.domain.enums.AssignmentSource;
import in.pragati.repo.*;

/** Generates the "PRAGATI Allocation Report" PDF (system generated, synthetic demo data). */
@Service
public class PdfReportService {

    private final AllocationRunRepository runs;
    private final DatasetSnapshotRepository snapshots;
    private final AssignmentRepository assignments;
    private final GroupMetricRepository groupMetrics;
    private final GeoMetricRepository geoMetrics;
    private final ConflictMetricRepository conflicts;
    private final CandidateProfileRepository profiles;
    private final OpportunityRepository opportunities;

    public PdfReportService(AllocationRunRepository runs, DatasetSnapshotRepository snapshots,
                            AssignmentRepository assignments, GroupMetricRepository groupMetrics,
                            GeoMetricRepository geoMetrics, ConflictMetricRepository conflicts,
                            CandidateProfileRepository profiles, OpportunityRepository opportunities) {
        this.runs = runs; this.snapshots = snapshots; this.assignments = assignments;
        this.groupMetrics = groupMetrics; this.geoMetrics = geoMetrics;
        this.conflicts = conflicts; this.profiles = profiles; this.opportunities = opportunities;
    }

    public byte[] generate(Long runId) {
        AllocationRun run = runs.findById(runId)
                .orElseThrow(() -> ApiException.notFound("Allocation run not found."));
        if (run.status != in.pragati.domain.enums.RunStatus.COMPLETED) {
            throw ApiException.badRequest("Reports are available for completed allocation runs only.");
        }
        DatasetSnapshot snap = snapshots.findById(run.snapshotId).orElse(null);
        List<Assignment> global = assignments.findByRunIdAndSource(run.id, AssignmentSource.GLOBAL);
        List<Assignment> baseline = assignments.findByRunIdAndSource(run.id, AssignmentSource.BASELINE);
        try {
            Document doc = new Document(com.lowagie.text.PageSize.A4, 48, 48, 56, 48);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
            Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 8.5f);

            try {
                byte[] logo = readLogo();
                if (logo != null) {
                    Image img = Image.getInstance(logo);
                    // Preserve the logo's aspect ratio — never stretch or crop the wordmark.
                    float h = 44f;
                    img.scaleAbsolute(h * img.getWidth() / img.getHeight(), h);
                    img.setAlignment(Element.ALIGN_LEFT);
                    doc.add(img);
                }
            } catch (Exception ignored) { }
            doc.add(new Paragraph("PRAGATI Allocation Report", h1));
            doc.add(new Paragraph("AI Smart Allocation for PM Internship Scheme (SIH25033)", small));
            doc.add(new Paragraph(" "));

            PdfPTable meta = table(2, body);
            addRow(meta, body, "Allocation run", "#" + run.number + "  ·  " + Labels.label(run.status)
                    + (run.solverStatus != null ? "  (" + Labels.label(run.solverStatus) + ")" : ""));
            addRow(meta, body, "Scenario", snap != null ? Labels.label(snap.scenario) : run.scenario.name());
            addRow(meta, body, "Dataset", snap != null
                    ? snap.candidateCount + " candidates · " + snap.opportunityCount + " opportunities · " + snap.seatCount + " seats"
                    : "—");
            addRow(meta, body, "Policy", run.policyName + " (version " + run.policyVersion + ")");
            addRow(meta, body, "Weights", run.weightsJson);
            addRow(meta, body, "Solver", "OR-Tools CP-SAT · status " + (run.solverStatus == null ? "—" : Labels.label(run.solverStatus))
                    + " · " + (run.solverRuntimeMs / 1000.0) + "s");
            doc.add(meta);
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("1. Allocation outcomes", h2));
            double avgSuit = global.isEmpty() ? 0 : Math.round(global.stream().mapToDouble(a -> a.suitability).average().orElse(0) * 10) / 10.0;
            long seats = snap != null ? snap.seatCount : 0;
            doc.add(new Paragraph("Candidates allocated: " + global.size() + " of "
                    + (snap != null ? snap.candidateCount : "—")
                    + "   ·   Average suitability: " + avgSuit + "%"
                    + "   ·   Seat utilization: " + (seats > 0 ? Math.round(global.size() * 1000.0 / seats) / 10.0 : 0) + "%", body));
            doc.add(new Paragraph("Sequential baseline allocated: " + baseline.size()
                    + (baseline.isEmpty() ? "" : " (average suitability "
                    + Math.round(baseline.stream().mapToDouble(a -> a.suitability).average().orElse(0) * 10) / 10.0 + "%)"), body));
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("2. Fairness by group", h2));
            PdfPTable fair = table(4, body);
            fair.addCell(cell(body, "Group")); fair.addCell(cell(body, "Population"));
            fair.addCell(cell(body, "Allocated")); fair.addCell(cell(body, "Rate"));
            for (GroupMetric m : groupMetrics.findByRunId(run.id).stream()
                    .filter(g -> g.groupType.equals("RURAL_URBAN") || g.groupType.equals("STATE")).toList()) {
                fair.addCell(cell(body, (m.groupType.equals("STATE") ? m.groupValue : m.groupValue)));
                fair.addCell(cell(body, String.valueOf(m.population)));
                fair.addCell(cell(body, String.valueOf(m.allocated)));
                fair.addCell(cell(body, m.allocationRate + "%"));
            }
            doc.add(fair);
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("3. Geography summary", h2));
            PdfPTable geo = table(5, body);
            geo.addCell(cell(body, "State")); geo.addCell(cell(body, "Demand"));
            geo.addCell(cell(body, "Capacity")); geo.addCell(cell(body, "Allocated"));
            geo.addCell(cell(body, "Unmet demand"));
            for (GeoMetric m : geoMetrics.findByRunId(run.id)) {
                geo.addCell(cell(body, m.state));
                geo.addCell(cell(body, String.valueOf(m.demand)));
                geo.addCell(cell(body, String.valueOf(m.capacity)));
                geo.addCell(cell(body, String.valueOf(m.allocated)));
                geo.addCell(cell(body, String.valueOf(m.unmetDemand)));
            }
            doc.add(geo);
            doc.add(new Paragraph(" "));

            List<ConflictMetric> top = conflicts.findByRunId(run.id).stream()
                    .sorted((a, b) -> Integer.compare(b.unmetDemand, a.unmetDemand)).limit(5).toList();
            if (!top.isEmpty()) {
                doc.add(new Paragraph("4. Most contested opportunities", h2));
                PdfPTable cf = table(4, body);
                cf.addCell(cell(body, "Opportunity")); cf.addCell(cell(body, "Eligible"));
                cf.addCell(cell(body, "Seats")); cf.addCell(cell(body, "Unmet demand"));
                for (ConflictMetric m : top) {
                    String title = opportunities.findById(m.opportunityId).map(o -> o.title).orElse("Opportunity");
                    cf.addCell(cell(body, title));
                    cf.addCell(cell(body, String.valueOf(m.eligibleCount)));
                    cf.addCell(cell(body, String.valueOf(m.seatCount)));
                    cf.addCell(cell(body, String.valueOf(m.unmetDemand)));
                }
                doc.add(cf);
                doc.add(new Paragraph(" "));
            }

            doc.add(new Paragraph("5. Provenance", h2));
            doc.add(new Paragraph("Run ID: " + run.runCode + "   ·   Dataset fingerprint: "
                    + (snap != null ? snap.fingerprint : "—") + "   ·   Policy: "
                    + run.policyKey + " " + (run.policyVersion != null && run.policyVersion.startsWith("v") ? run.policyVersion : "v" + run.policyVersion)
                    + "   ·   Optimizer: OR-Tools CP-SAT (workers 1, seed 0)", small));
            doc.add(new Paragraph("Generated by the PRAGATI allocation engine from stored run data.", small));
            doc.add(new Paragraph(" "));
            Paragraph footer = new Paragraph("Synthetic Demo Data — System Generated. "
                    + "This report was produced automatically from a synthetic demonstration environment.", small);
            doc.add(footer);
            doc.close();
            return out.toByteArray();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.serviceUnavailable("The report could not be generated. Please try again.");
        }
    }

    private byte[] readLogo() {
        try {
            ClassPathResource res = new ClassPathResource("logo/logo.png");
            return res.exists() ? res.getInputStream().readAllBytes() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private PdfPTable table(int cols, Font f) {
        PdfPTable t = new PdfPTable(cols);
        t.setWidthPercentage(100);
        return t;
    }

    private void addRow(PdfPTable t, Font f, String k, String v) {
        PdfPCell a = new PdfPCell(new Phrase(k, f));
        a.setBorder(Rectangle.NO_BORDER);
        PdfPCell b = new PdfPCell(new Phrase(v, f));
        b.setBorder(Rectangle.NO_BORDER);
        t.addCell(a);
        t.addCell(b);
    }

    private PdfPCell cell(Font f, String v) {
        PdfPCell c = new PdfPCell(new Phrase(v, f));
        return c;
    }
}
