package com.google.enterprise.connector.filesystem;

import com.google.enterprise.connector.util.ChecksumGenerator;
import com.google.enterprise.connector.util.diffing.DocumentSink;
import com.google.enterprise.connector.util.diffing.LoggingDocumentSink;
import com.google.enterprise.connector.util.SystemClock;
import com.google.enterprise.connector.util.diffing.TraversalContextManager;
import com.google.enterprise.connector.spi.RepositoryDocumentException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FileDocumentSnapshotRepositoryList
    extends ArrayList<FileDocumentSnapshotRepository> {
  private static final DocumentSink DOCUMENT_SINK = new LoggingDocumentSink();
  private final ChecksumGenerator checksumGenerator;
  private final PathParser pathParser;
  private final Collection<String> startPaths;
  private final FilePatternMatcher filePatternMatcher;
  private final Credentials credentials;
  private final TraversalContextManager traversalContextManager;
  private final FileSystemTypeRegistry fileSystemTypeRegistry;
  private final boolean pushAcls;
  private final boolean markAllDocumentsPublic;

  /**
   * Constructs a {@link FileDocumentSnapshotRepositoryList} from
   * parameters which may be obtained using {@link FileConnectorType}.
   *
   * @throws RepositoryDocumentException if this fails due to an environmental
   * issue or an invalid configuration.
   */
  FileDocumentSnapshotRepositoryList(ChecksumGenerator checksumGenerator,
      PathParser pathParser, List<String> userEnteredStartPaths,
      List<String> includePatterns, List<String> excludePatterns,
      String userName, String password, String domainName,
      TraversalContextManager traversalContextManager,
      FileSystemTypeRegistry fileSystemTypeRegistry,
      boolean pushAcls, boolean markAllDocumentsPublic)
      throws RepositoryDocumentException {
    this.checksumGenerator = checksumGenerator;
    this.pathParser = pathParser;
    this.startPaths = normalizeStartPaths(userEnteredStartPaths);
    this.filePatternMatcher = FileConnectorType.newFilePatternMatcher(
        includePatterns, excludePatterns);
    this.credentials = FileConnectorType.newCredentials(
        domainName, userName, password);
    this.traversalContextManager = traversalContextManager;
    this.fileSystemTypeRegistry = fileSystemTypeRegistry;
    this.pushAcls = pushAcls;
    this.markAllDocumentsPublic = markAllDocumentsPublic;
    for (String startPath : startPaths) {
      add(newFileDocumentSnapshotRepository(startPath));
    }
  }

  private static Collection<String> normalizeStartPaths(
      List<String> userEnteredStartPaths) {
    List<String> result =
      FileConnectorType.filterUserEnteredList(userEnteredStartPaths);
    for (int ix = 0; ix < result.size(); ix++) {
      String path = result.get(ix);
      if (!path.endsWith("/")) {
        path += "/";
        result.set(ix, path);
      }
    }
    return Collections.unmodifiableCollection(result);
  }

  private FileDocumentSnapshotRepository newFileDocumentSnapshotRepository(
      String startPath) throws RepositoryDocumentException {
    ReadonlyFile<?> root = pathParser.getFile(startPath, credentials);
    if (root == null) {
      throw new RepositoryDocumentException("failed to open start path: "
          + startPath);
    }

    FileDocumentSnapshotRepository repository =
      new FileDocumentSnapshotRepository(root, DOCUMENT_SINK,
          filePatternMatcher, traversalContextManager, checksumGenerator,
          SystemClock.INSTANCE, new MimeTypeFinder(), credentials,
          fileSystemTypeRegistry, pushAcls, markAllDocumentsPublic);
    return repository;
  }
}
