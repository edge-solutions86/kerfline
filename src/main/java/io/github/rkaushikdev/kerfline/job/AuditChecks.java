package io.github.rkaushikdev.kerfline.job;

/**
 * What an audit looked at, which it reports even when it finds nothing: the joints on the
 * register, the entries in the journal, the registered joints never shot and the nonconformances
 * still open.
 */
public record AuditChecks(int joints, int entries, int neverShot, int openNonconformances) {}
