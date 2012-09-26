/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.easyant.core.services.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.easyant.core.EasyAntConstants;
import org.apache.easyant.core.EasyAntMagicNames;
import org.apache.easyant.core.ant.ProjectUtils;
import org.apache.easyant.core.descriptor.EasyAntModuleDescriptor;
import org.apache.easyant.core.descriptor.PropertyDescriptor;
import org.apache.easyant.core.parser.DefaultEasyAntXmlModuleDescriptorParser;
import org.apache.easyant.core.parser.EasyAntModuleDescriptorParser;
import org.apache.easyant.core.report.EasyAntReport;
import org.apache.easyant.core.report.ExtensionPointReport;
import org.apache.easyant.core.report.ImportedModuleReport;
import org.apache.easyant.core.report.ParameterReport;
import org.apache.easyant.core.report.ParameterType;
import org.apache.easyant.core.report.TargetReport;
import org.apache.easyant.core.services.PluginService;
import org.apache.easyant.tasks.AbstractImport;
import org.apache.easyant.tasks.Import;
import org.apache.easyant.tasks.ImportTestModule;
import org.apache.easyant.tasks.LoadModule;
import org.apache.easyant.tasks.ParameterTask;
import org.apache.ivy.Ivy;
import org.apache.ivy.ant.IvyAntSettings;
import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.parser.ModuleDescriptorParserRegistry;
import org.apache.ivy.plugins.repository.url.URLResource;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.util.Message;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.ComponentHelper;
import org.apache.tools.ant.ExtensionPoint;
import org.apache.tools.ant.Location;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.UnknownElement;
import org.apache.tools.ant.taskdefs.Property;
import org.apache.tools.ant.types.Path;

public class DefaultPluginServiceImpl implements PluginService {

    private final EasyAntModuleDescriptorParser parser;

    private final Ivy ivyInstance;
    private final IvyAntSettings easyantIvySettings;

    /**
     * This is the default constructor, the IvyContext should be the IvyContext configured to the easyant ivy instance
     * 
     * @param easyantIvySettings
     *            the easyant ivy instance
     */
    public DefaultPluginServiceImpl(final IvyAntSettings easyantIvySettings) {
        this(easyantIvySettings, new DefaultEasyAntXmlModuleDescriptorParser());
    }

    /**
     * A custom constructor if you want to specify your own parser / configuration service, you should use this
     * constructor the IvyContext should be the IvyContext configured to the easyant ivy instance
     * 
     * @param easyantIvySetings
     *            the easyant ivy instance
     * @param parser
     *            a valid easyantModuleDescriptor
     */
    public DefaultPluginServiceImpl(final IvyAntSettings easyantIvySetings, EasyAntModuleDescriptorParser parser) {
        this.easyantIvySettings = easyantIvySetings;
        this.ivyInstance = easyantIvySetings.getConfiguredIvyInstance(easyantIvySetings);
        if (parser == null) {
            throw new IllegalArgumentException("You must set a valid easyant module descriptor parser");
        }
        this.parser = parser;
        ModuleDescriptorParserRegistry.getInstance().addParser(parser);
    }

    public EasyAntReport getPluginInfo(File pluginIvyFile, File sourceDirectory, String conf) throws Exception {
        IvyContext.pushNewContext().setIvy(ivyInstance);
        EasyAntReport eaReport = null;
        try {

            ResolveOptions resolveOptions = new ResolveOptions();
            resolveOptions.setLog(ResolveOptions.LOG_QUIET);
            resolveOptions.setConfs(conf.split(","));
            ResolveReport report = IvyContext.getContext().getIvy().getResolveEngine()
                    .resolve(pluginIvyFile.toURI().toURL(), resolveOptions);
            eaReport = new EasyAntReport();
            eaReport.setResolveReport(report);
            eaReport.setModuleDescriptor(report.getModuleDescriptor());

            Project project = buildProject(null);

            // emulate top level project
            ImportTestModule importTestModule = new ImportTestModule();
            importTestModule.setModuleIvy(pluginIvyFile);
            importTestModule.setSourceDirectory(sourceDirectory);
            importTestModule.setOwningTarget(ProjectUtils.createTopLevelTarget());
            importTestModule.setLocation(new Location(ProjectUtils.emulateMainScript(project).getAbsolutePath()));
            importTestModule.setProject(project);
            importTestModule.execute();

            analyseProject(project, eaReport, conf);
        } catch (Exception e) {
            throw new Exception("An error occured while fetching plugin informations : " + e.getMessage(), e);
        } finally {
            IvyContext.popContext();
        }
        return eaReport;

    }

    public EasyAntReport getPluginInfo(final ModuleRevisionId moduleRevisionId, String conf) throws Exception {

        IvyContext.pushNewContext().setIvy(ivyInstance);
        EasyAntReport eaReport = null;
        try {

            ResolveOptions resolveOptions = new ResolveOptions();
            resolveOptions.setLog(ResolveOptions.LOG_QUIET);
            resolveOptions.setConfs(conf.split(","));
            final ResolveReport report = IvyContext.getContext().getIvy().getResolveEngine()
                    .resolve(moduleRevisionId, resolveOptions, true);
            eaReport = new EasyAntReport();
            eaReport.setResolveReport(report);
            eaReport.setModuleDescriptor(report.getModuleDescriptor());

            Project project = buildProject(null);

            AbstractImport abstractImport = new AbstractImport() {
                @Override
                public void execute() throws BuildException {
                    Path path = createModulePath(moduleRevisionId);
                    File antFile = null;
                    for (int j = 0; j < report.getConfigurationReport(getMainConf()).getAllArtifactsReports().length; j++) {
                        ArtifactDownloadReport artifact = report.getConfigurationReport(getMainConf())
                                .getAllArtifactsReports()[j];

                        if ("ant".equals(artifact.getType())) {
                            antFile = artifact.getLocalFile();
                        } else if ("jar".equals(artifact.getType())) {
                            path.createPathElement().setLocation(artifact.getLocalFile());
                        } else {
                            handleOtherResourceFile(moduleRevisionId, artifact.getName(), artifact.getExt(),
                                    artifact.getLocalFile());
                        }
                    }
                    if (antFile != null && antFile.exists()) {
                        ProjectHelper.configureProject(getProject(), antFile);
                    }
                }
            };

            abstractImport.setProject(project);
            // location ?
            abstractImport.execute();

            analyseProject(project, eaReport, conf);
        } catch (Exception e) {
            throw new Exception("An error occured while fetching plugin informations : " + e.getMessage(), e);
        } finally {
            IvyContext.popContext();
        }
        return eaReport;

    }

    private Project buildProject(Map<String, String> properties) {
        Project project = new Project();
        project.setNewProperty(EasyAntMagicNames.AUDIT_MODE, "true");
        project.setNewProperty(EasyAntMagicNames.SKIP_CORE_REVISION_CHECKER, "true");
        project.addReference(EasyAntMagicNames.EASYANT_IVY_INSTANCE, easyantIvySettings);
        if (properties != null) {
            for (Entry<String, String> entry : properties.entrySet()) {
                project.setNewProperty(entry.getKey(), entry.getValue());
            }
        }
        project.init();
        ProjectHelper helper = ProjectHelper.getProjectHelper();
        helper.getImportStack().addElement(ProjectUtils.emulateMainScript(project));
        project.addReference(ProjectHelper.PROJECTHELPER_REFERENCE, helper);
        return project;
    }

    private void analyseProject(Project project, EasyAntReport eaReport, String conf) throws IOException, Exception {
        Map<String, Target> targets = ProjectUtils.removeDuplicateTargets(project.getTargets());
        for (Target target : targets.values()) {
            handleTarget(target, eaReport);
            for (int i = 0; i < target.getTasks().length; i++) {
                Task task = target.getTasks()[i];
                Class<?> taskClass = ComponentHelper.getComponentHelper(project).getComponentClass(task.getTaskType());
                if (taskClass == null) {
                    continue;
                }
                if (ParameterTask.class.isAssignableFrom(taskClass)) {
                    ParameterTask parameterTask = (ParameterTask) maybeConfigureTask(task);
                    handleParameterTask(parameterTask, eaReport);
                }
                if (Property.class.isAssignableFrom(taskClass)) {
                    Property propertyTask = (Property) maybeConfigureTask(task);
                    handleProperty(propertyTask, eaReport);
                }
                if (Import.class.isAssignableFrom(taskClass)) {
                    Import importTask = (Import) maybeConfigureTask(task);
                    handleImport(importTask, eaReport, conf);
                }
            }
        }
    }

    private Object maybeConfigureTask(Task task) {
        if (task.getRuntimeConfigurableWrapper().getProxy() instanceof UnknownElement) {
            UnknownElement ue = (UnknownElement) task.getRuntimeConfigurableWrapper().getProxy();
            ue.maybeConfigure();
            return ue.getRealThing();
        } else if (task instanceof UnknownElement) {
            UnknownElement ue = (UnknownElement) task;
            ue.maybeConfigure();
            return ue.getRealThing();
        } else {
            return task;
        }
    }

    private void handleImport(Import importTask, EasyAntReport eaReport, String conf) throws Exception {
        ImportedModuleReport importedModuleReport = new ImportedModuleReport();

        importedModuleReport.setModuleMrid(importTask.getMrid());
        importedModuleReport.setOrganisation(importTask.getOrganisation());
        importedModuleReport.setModule(importTask.getModule());
        importedModuleReport.setRevision(importTask.getRevision());
        importedModuleReport.setMandatory(importTask.isMandatory());
        importedModuleReport.setMode(importTask.getMode());
        importedModuleReport.setAs(importTask.getAs());
        importedModuleReport
                .setEasyantReport(getPluginInfo(ModuleRevisionId.parse(importedModuleReport.getModuleMrid())));
        eaReport.addImportedModuleReport(importedModuleReport);

        Message.debug("Ant file import another module called : " + importedModuleReport.getModuleMrid() + " with mode "
                + importedModuleReport.getMode());
    }

    private void handleProperty(Property property, EasyAntReport eaReport) throws IOException {

        if (property.getFile() != null) {
            Properties propToLoad = new Properties();
            File f = property.getFile();
            if (f.exists()) {
                try {
                    propToLoad.load(new FileInputStream(f));
                    for (Iterator<?> iter = propToLoad.keySet().iterator(); iter.hasNext();) {
                        String key = (String) iter.next();
                        PropertyDescriptor propertyDescriptor = new PropertyDescriptor(key);
                        propertyDescriptor.setValue(propToLoad.getProperty(key));
                        eaReport.addPropertyDescriptor(propertyDescriptor.getName(), propertyDescriptor);
                    }

                } catch (IOException e) {
                    IOException ioe = new IOException("Unable to parse the property file :" + property.getFile());
                    ioe.initCause(e);
                    throw ioe;
                }
            }
        }
        if (property.getName() != null) {
            PropertyDescriptor propertyDescriptor = new PropertyDescriptor(property.getName());
            propertyDescriptor.setValue(property.getValue());
            eaReport.addPropertyDescriptor(property.getName(), propertyDescriptor);
        }
    }

    private void handleParameterTask(ParameterTask parameterTask, EasyAntReport eaReport) {
        if (parameterTask.getProperty() != null) {
            PropertyDescriptor propertyDescriptor = new PropertyDescriptor(parameterTask.getProperty());
            propertyDescriptor.setDefaultValue(parameterTask.getDefault());
            propertyDescriptor.setRequired(parameterTask.isRequired());
            propertyDescriptor.setDescription(parameterTask.getDescription());
            Message.debug("Ant file has a property called : " + propertyDescriptor.getName());
            eaReport.addPropertyDescriptor(propertyDescriptor.getName(), propertyDescriptor);
        } else if (parameterTask.getPath() != null) {
            ParameterReport parameterReport = new ParameterReport(ParameterType.PATH);
            parameterReport.setName(parameterTask.getPath());
            parameterReport.setRequired(parameterTask.isRequired());
            parameterReport.setDescription(parameterTask.getDescription());
            eaReport.addParameterReport(parameterReport);
            Message.debug("Ant file has a path called : " + parameterReport.getName());
        }
    }

    private void handleTarget(Target target, EasyAntReport eaReport) {
        if (!"".equals(target.getName())) {
            boolean isExtensionPoint = target instanceof ExtensionPoint;
            if (!isExtensionPoint) {
                TargetReport targetReport = new TargetReport();
                targetReport.setName(target.getName());
                StringBuilder sb = new StringBuilder();
                Enumeration<?> targetDeps = target.getDependencies();
                while (targetDeps.hasMoreElements()) {
                    String t = (String) targetDeps.nextElement();
                    sb.append(t);
                    if (targetDeps.hasMoreElements()) {
                        sb.append(",");
                    }
                }
                targetReport.setDepends(sb.toString());
                targetReport.setDescription(target.getDescription());
                targetReport.setIfCase(target.getIf());
                targetReport.setUnlessCase(target.getUnless());
                for (Iterator<?> iterator = target.getProject().getTargets().values().iterator(); iterator.hasNext();) {
                    Target currentTarget = (Target) iterator.next();
                    if (currentTarget instanceof ExtensionPoint) {
                        Enumeration<?> dependencies = currentTarget.getDependencies();
                        while (dependencies.hasMoreElements()) {
                            String dep = (String) dependencies.nextElement();
                            if (dep.equals(target.getName())) {
                                targetReport.setExtensionPoint(currentTarget.getName());
                            }
                        }

                    }
                }

                eaReport.addTargetReport(targetReport);

                Message.debug("Ant file has a target called : " + targetReport.getName());
            } else {
                ExtensionPointReport extensionPoint = new ExtensionPointReport(target.getName());
                StringBuilder sb = new StringBuilder();
                Enumeration<?> targetDeps = target.getDependencies();
                while (targetDeps.hasMoreElements()) {
                    String t = (String) targetDeps.nextElement();
                    sb.append(t);
                    if (targetDeps.hasMoreElements()) {
                        sb.append(",");
                    }
                }
                extensionPoint.setDepends(sb.toString());
                extensionPoint.setDescription(target.getDescription());
                eaReport.addExtensionPointReport(extensionPoint);
                Message.debug("Ant file has an extensionPoint called : " + extensionPoint.getName());
            }
        }
    }

    public EasyAntReport getPluginInfo(ModuleRevisionId moduleRevisionId) throws Exception {
        return getPluginInfo(moduleRevisionId, "default");
    }

    public EasyAntReport getPluginInfo(String moduleRevisionId) throws Exception {
        ModuleRevisionId module = buildModuleRevisionId(moduleRevisionId, PluginType.PLUGIN);
        return getPluginInfo(module);
    }

    public EasyAntReport getBuildTypeInfo(String moduleRevisionId) throws Exception {
        ModuleRevisionId module = buildModuleRevisionId(moduleRevisionId, PluginType.BUILDTYPE);
        return getPluginInfo(module);
    }

    public EasyAntModuleDescriptor getEasyAntModuleDescriptor(File moduleDescriptor) throws Exception {
        if (moduleDescriptor == null)
            throw new Exception("moduleDescriptor cannot be null");
        if (!moduleDescriptor.exists()) {
            throw new Exception("imposible to find the specified module descriptor"
                    + moduleDescriptor.getAbsolutePath());
        }
        IvyContext.pushNewContext().setIvy(ivyInstance);
        // First we need to parse the specified file to retrieve all the easyant
        // stuff
        parser.parseDescriptor(ivyInstance.getSettings(), moduleDescriptor.toURI().toURL(), new URLResource(
                moduleDescriptor.toURI().toURL()), true);
        EasyAntModuleDescriptor md = parser.getEasyAntModuleDescriptor();
        IvyContext.popContext();
        return md;
    }

    public EasyAntReport generateEasyAntReport(File moduleDescriptor, File optionalAntModule, File overrideAntModule)
            throws Exception {
        EasyAntReport eaReport = new EasyAntReport();
        EasyAntModuleDescriptor md = getEasyAntModuleDescriptor(moduleDescriptor);
        eaReport.setModuleDescriptor(md.getIvyModuleDescriptor());

        Project p = buildProject(null);
        Target implicitTarget = ProjectUtils.createTopLevelTarget();
        p.addTarget("", implicitTarget);
        LoadModule loadModule = new LoadModule();
        loadModule.setBuildModule(moduleDescriptor);
        loadModule.setBuildFile(optionalAntModule);
        loadModule.setOwningTarget(implicitTarget);
        loadModule.setLocation(new Location(ProjectUtils.emulateMainScript(p).getAbsolutePath()));
        loadModule.setProject(p);
        loadModule.execute();
        ProjectHelper projectHelper = (ProjectHelper) p.getReference(ProjectHelper.PROJECTHELPER_REFERENCE);
        ProjectUtils.injectTargetIntoExtensionPoint(p, projectHelper);
        analyseProject(p, eaReport, "default");

        return eaReport;
    }

    public ModuleRevisionId[] search(String organisation, String moduleName, String revision, String branch,
            String matcher, String resolver) throws Exception {
        IvySettings settings = ivyInstance.getSettings();

        if (moduleName == null && PatternMatcher.EXACT.equals(matcher)) {
            throw new Exception("no module name provided for ivy repository graph task: "
                    + "It can either be set explicitely via the attribute 'module' or "
                    + "via 'ivy.module' property or a prior call to <resolve/>");
        } else if (moduleName == null && !PatternMatcher.EXACT.equals(matcher)) {
            moduleName = PatternMatcher.ANY_EXPRESSION;
        }
        ModuleRevisionId mrid = ModuleRevisionId.newInstance(organisation, moduleName, revision);

        ModuleRevisionId criteria = null;

        if ((revision == null) || settings.getVersionMatcher().isDynamic(mrid)) {
            criteria = new ModuleRevisionId(new ModuleId(organisation, moduleName), branch, "*");
        } else {
            criteria = new ModuleRevisionId(new ModuleId(organisation, moduleName), branch, revision);
        }

        PatternMatcher patternMatcher = settings.getMatcher(matcher);
        if ("*".equals(resolver)) {
            // search in all resolvers. this can be quite slow for complex
            // repository configurations
            // with ChainResolvers, since resolvers in chains will be searched
            // multiple times.
            return ivyInstance.listModules(criteria, patternMatcher);
        } else {
            // limit search to the specified resolver.
            DependencyResolver dependencyResolver = resolver == null ? settings.getDefaultResolver() : settings
                    .getResolver(resolver);
            if (dependencyResolver == null) {
                throw new IllegalArgumentException("Unknown dependency resolver for search: " + resolver);
            }

            ivyInstance.pushContext();
            try {
                return ivyInstance.getSearchEngine().listModules(dependencyResolver, criteria, patternMatcher);
            } finally {
                ivyInstance.popContext();
            }
        }
    }

    public ModuleRevisionId[] search(String organisation, String moduleName) throws Exception {
        return search(organisation, moduleName, null, null, PatternMatcher.EXACT_OR_REGEXP, null);
    }

    public String[] searchModule(String organisation, String moduleName) throws Exception {
        ModuleRevisionId[] mrids = search(organisation, moduleName);
        String[] result = new String[mrids.length];
        for (int i = 0; i < mrids.length; i++) {
            result[i] = mrids[i].toString();
        }
        return result;
    }

    public String getDescription(ModuleRevisionId mrid) {
        ResolvedModuleRevision rmr = ivyInstance.findModule(mrid);
        return rmr.getDescriptor().getDescription();
    }

    public String getPluginDescription(String moduleRevisionId) {
        ModuleRevisionId module = buildModuleRevisionId(moduleRevisionId, PluginType.PLUGIN);
        return getDescription(module);
    }

    public String getBuildTypeDescription(String moduleRevisionId) {
        ModuleRevisionId module = buildModuleRevisionId(moduleRevisionId, PluginType.BUILDTYPE);

        return getDescription(module);
    }

    private ModuleRevisionId buildModuleRevisionId(String moduleRevisionId, PluginType pluginType) {
        String mrid = moduleRevisionId;
        if (!mrid.matches(".*#.*")) {
            if (pluginType.equals(PluginType.BUILDTYPE)) {
                Message.debug("No organisation specified for buildtype " + mrid + " using the default one");

                mrid = EasyAntConstants.EASYANT_BUILDTYPES_ORGANISATION + "#" + mrid;

            } else {
                Message.debug("No organisation specified for plugin " + mrid + " using the default one");

                mrid = EasyAntConstants.EASYANT_PLUGIN_ORGANISATION + "#" + mrid;
            }
        }
        ModuleRevisionId module = ModuleRevisionId.parse(mrid);
        return module;
    }

    public EasyAntReport generateEasyAntReport(File moduleDescriptor) throws Exception {
        return generateEasyAntReport(moduleDescriptor, null, null);
    }

    private enum PluginType {
        BUILDTYPE, PLUGIN
    }

}
