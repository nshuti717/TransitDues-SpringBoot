package rw.ac.auca.transitdues.duepayment.service;

public record BulkIssueResult(int issuedCount, int skippedCount) {

    public String summary() {
        return "Issued " + issuedCount + (issuedCount == 1 ? " due" : " dues")
                + ", skipped " + skippedCount + " already issued.";
    }
}
