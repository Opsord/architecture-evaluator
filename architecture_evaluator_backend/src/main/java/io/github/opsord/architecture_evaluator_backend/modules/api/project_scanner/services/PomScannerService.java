package io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.services;

import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.instances.pom.ParentSectionDTO;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.instances.pom.PomDependencyInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.instances.pom.PomFileInstance;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for scanning and parsing Maven pom.xml files.
 */
@Service
@RequiredArgsConstructor
public class PomScannerService {

    private static final String TAG_GROUP_ID = "groupId";
    private static final String TAG_ARTIFACT_ID = "artifactId";
    private static final String TAG_VERSION = "version";
    private static final String TAG_DESCRIPTION = "description";
    private static final String TAG_URL = "url";
    private static final String TAG_LICENSE = "license";
    private static final String TAG_DEVELOPER = "developer";
    private static final String TAG_NAME = "name";
    private static final String TAG_PROPERTIES = "properties";
    private static final String TAG_DEPENDENCIES = "dependencies";
    private static final String TAG_DEPENDENCY = "dependency";
    private static final String TAG_PARENT = "parent";

    private static final List<String> JAVA_VERSION_PROPERTIES = List.of(
            "java.version",
            "maven.compiler.release",
            "maven.compiler.source",
            "maven.compiler.target");

    private static final String FEATURE_DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String FEATURE_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    private static final String FEATURE_EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
    private static final String FEATURE_LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private static final Logger logger = LoggerFactory.getLogger(PomScannerService.class);
    private final ScannerService scannerService;

    public Optional<PomFileInstance> scanPomFile(File projectDirectory) {
        if (projectDirectory == null || !projectDirectory.isDirectory()) {
            logger.warn("Invalid project directory: {}",
                    projectDirectory != null ? projectDirectory.getAbsolutePath() : "null");
            return Optional.empty();
        }

        File pomFile = scannerService.findPomFile(projectDirectory);
        if (pomFile == null) {
            logger.warn("No pom.xml found in directory: {}", projectDirectory.getAbsolutePath());
            return Optional.empty();
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(FEATURE_DISALLOW_DOCTYPE, true);
            factory.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
            factory.setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
            factory.setFeature(FEATURE_LOAD_EXTERNAL_DTD, false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(pomFile);
            Element root = document.getDocumentElement();
            root.normalize();

            PomFileInstance pomFileInstance = new PomFileInstance();
            ParentSectionDTO parentSection = getParentInfo(root);
            pomFileInstance.setParentSection(parentSection);

            String groupId = getDirectChildText(root, TAG_GROUP_ID);
            if ((groupId == null || groupId.isBlank()) && parentSection != null) {
                groupId = parentSection.getGroupId();
            }
            String version = getDirectChildText(root, TAG_VERSION);
            if ((version == null || version.isBlank()) && parentSection != null) {
                version = parentSection.getVersion();
            }

            pomFileInstance.setGroupId(groupId);
            pomFileInstance.setArtifactId(getDirectChildText(root, TAG_ARTIFACT_ID));
            pomFileInstance.setVersion(version);
            pomFileInstance.setDescription(getDirectChildText(root, TAG_DESCRIPTION));
            pomFileInstance.setUrl(getDirectChildText(root, TAG_URL));
            pomFileInstance.setLicense(getLicense(document));
            pomFileInstance.setDevelopers(getDevelopers(document));
            pomFileInstance.setJavaVersion(getJavaVersion(root));
            pomFileInstance.setDependencies(parseDependencies(root));

            return Optional.of(pomFileInstance);
        } catch (ParserConfigurationException e) {
            logger.error("Parser configuration error while parsing pom.xml in directory: {}", projectDirectory.getAbsolutePath(), e);
        } catch (SAXException e) {
            logger.error("SAX error while parsing pom.xml in directory: {}", projectDirectory.getAbsolutePath(), e);
        } catch (IOException e) {
            logger.error("IO error while parsing pom.xml in directory: {}", projectDirectory.getAbsolutePath(), e);
        } catch (Exception e) {
            logger.error("Unexpected error while parsing pom.xml in directory: {}", projectDirectory.getAbsolutePath(), e);
        }
        return Optional.empty();
    }

    private String getJavaVersion(Element root) {
        Element properties = getDirectChildElement(root, TAG_PROPERTIES);
        if (properties == null) {
            return null;
        }
        for (String propName : JAVA_VERSION_PROPERTIES) {
            String value = getDirectChildText(properties, propName);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private List<PomDependencyInstance> parseDependencies(Element root) {
        List<PomDependencyInstance> dependencyList = new ArrayList<>();
        Element dependenciesElement = getDirectChildElement(root, TAG_DEPENDENCIES);
        if (dependenciesElement == null) {
            return dependencyList;
        }

        NodeList children = dependenciesElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && TAG_DEPENDENCY.equals(child.getNodeName())) {
                Element dependency = (Element) child;
                String groupId = getDirectChildText(dependency, TAG_GROUP_ID);
                String artifactId = getDirectChildText(dependency, TAG_ARTIFACT_ID);
                String version = getDirectChildText(dependency, TAG_VERSION);

                if (groupId != null && artifactId != null) {
                    dependencyList.add(new PomDependencyInstance(groupId, artifactId, version));
                }
            }
        }

        return dependencyList;
    }

    private ParentSectionDTO getParentInfo(Element root) {
        Element parentElement = getDirectChildElement(root, TAG_PARENT);
        if (parentElement != null) {
            ParentSectionDTO parentDTO = new ParentSectionDTO();
            parentDTO.setGroupId(getDirectChildText(parentElement, TAG_GROUP_ID));
            parentDTO.setArtifactId(getDirectChildText(parentElement, TAG_ARTIFACT_ID));
            parentDTO.setVersion(getDirectChildText(parentElement, TAG_VERSION));
            return parentDTO;
        }
        return null;
    }

    private String getLicense(Document document) {
        NodeList licenses = document.getElementsByTagName(TAG_LICENSE);
        if (licenses.getLength() > 0) {
            Element license = (Element) licenses.item(0);
            return getDirectChildText(license, TAG_NAME);
        }
        return null;
    }

    private List<String> getDevelopers(Document document) {
        List<String> developers = new ArrayList<>();
        NodeList developerNodes = document.getElementsByTagName(TAG_DEVELOPER);

        for (int i = 0; i < developerNodes.getLength(); i++) {
            Element developer = (Element) developerNodes.item(i);
            String name = getDirectChildText(developer, TAG_NAME);
            if (name != null) {
                developers.add(name);
            }
        }

        return developers;
    }

    private Element getDirectChildElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && tagName.equals(child.getNodeName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private String getDirectChildText(Element parent, String tagName) {
        Element child = getDirectChildElement(parent, tagName);
        if (child != null && child.getTextContent() != null) {
            return child.getTextContent().trim();
        }
        return null;
    }
}
