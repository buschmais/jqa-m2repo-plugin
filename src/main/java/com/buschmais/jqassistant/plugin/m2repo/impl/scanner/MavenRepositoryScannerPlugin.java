package com.buschmais.jqassistant.plugin.m2repo.impl.scanner;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Date;

import com.buschmais.jqassistant.core.scanner.api.Scanner;
import com.buschmais.jqassistant.core.scanner.api.ScannerContext;
import com.buschmais.jqassistant.core.scanner.api.Scope;
import com.buschmais.jqassistant.plugin.common.api.scanner.AbstractScannerPlugin;
import com.buschmais.jqassistant.plugin.m2repo.api.ArtifactProvider;
import com.buschmais.jqassistant.plugin.maven3.api.model.MavenRepositoryDescriptor;
import com.buschmais.jqassistant.plugin.maven3.api.scanner.MavenRepositoryResolver;
import com.buschmais.jqassistant.plugin.maven3.api.scanner.MavenScope;

import org.apache.maven.index.ArtifactInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A scanner for (remote) maven repositories.
 * 
 * @author pherklotz
 */
public class MavenRepositoryScannerPlugin
        extends AbstractScannerPlugin<URL, MavenRepositoryDescriptor> {

    public static final String DEFAULT_M2REPO_DIR = "./jqassistant/data/m2repo";

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenRepositoryScannerPlugin.class);

    private static final String PROPERTY_NAME_DIRECTORY = "m2repo.directory";

    private File localDirectory;

    /** {@inheritDoc} */
    @Override
    public boolean accepts(URL item, String path, Scope scope) throws IOException {
        return MavenScope.REPOSITORY == scope;
    }

    /** {@inheritDoc} */
    @Override
    public void configure() {
        localDirectory = new File(getStringProperty(PROPERTY_NAME_DIRECTORY, DEFAULT_M2REPO_DIR));
    }

    /** {@inheritDoc} */
    @Override
    public MavenRepositoryDescriptor scan(URL repositoryUrl, String path, Scope scope, Scanner scanner) throws IOException {
        if (!localDirectory.exists()) {
            LOGGER.info("Creating local maven repository directory {}", localDirectory.getAbsolutePath());
            localDirectory.mkdirs();
        }
        MavenRepositoryDescriptor repoDescriptor = MavenRepositoryResolver.resolve(scanner.getContext().getStore(), repositoryUrl.toString());
        AetherArtifactProvider artifactProvider = new AetherArtifactProvider(repositoryUrl, repoDescriptor, localDirectory);
        scan(artifactProvider, scanner);
        return repoDescriptor;
    }

    /**
     * Scan the repository represented by the given artifact provider.
     * 
     * @param artifactProvider
     *            The artifact provider.
     * @throws IOException
     *             If scanning fails.
     */
    public void scan(AetherArtifactProvider artifactProvider, Scanner scanner) throws IOException {
        // the MavenRepositoryDescriptor
        MavenIndex mavenIndex = artifactProvider.getMavenIndex();
        Date lastIndexUpdateTime = mavenIndex.getLastUpdateLocalRepo();
        MavenRepositoryDescriptor repositoryDescriptor = artifactProvider.getRepositoryDescriptor();
        Date lastScanTime = new Date(repositoryDescriptor.getLastUpdate());
        Date artifactsSince = lastIndexUpdateTime;
        if (lastIndexUpdateTime == null || lastIndexUpdateTime.after(lastScanTime)) {
            artifactsSince = lastScanTime;
        }
        mavenIndex.updateIndex();
        // Search artifacts
        ScannerContext context = scanner.getContext();
        context.push(ArtifactProvider.class, artifactProvider);
        try {
            Iterable<ArtifactInfo> searchResponse = mavenIndex.getArtifactsSince(artifactsSince);
            for (ArtifactInfo ai : searchResponse) {
                scanner.scan(ai, ai.toString(), MavenScope.REPOSITORY);
            }
        } finally {
            context.pop(ArtifactProvider.class);
        }
        mavenIndex.closeCurrentIndexingContext();
        repositoryDescriptor.setLastUpdate(System.currentTimeMillis());
    }
}
