/**
 * The append only journal every record goes into, with the queries, snapshots, migrations and
 * file import that read it back. Nothing is ever edited: each entry is checksummed and chained
 * onto the one before it, so an alteration anywhere shows.
 */
package io.github.rkaushikdev.kerfline.store;
