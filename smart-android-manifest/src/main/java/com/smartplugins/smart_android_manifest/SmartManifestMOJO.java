package com.smartplugins.smart_android_manifest;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.reflections.Reflections;
import org.reflections.scanners.Scanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.smartannotations.RegisterActivity;
import com.smartannotations.RegisterService;

@Mojo(name = "findAnnotations",
requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
defaultPhase=LifecyclePhase.PROCESS_CLASSES)
public final class SmartManifestMOJO extends AbstractMojo
{
    @Parameter(defaultValue="Scanning for smart annotations..", required=false)
    private Object message;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    private String outputDirectory;
    private File androidManifestDirectory;
    
    public void execute() throws MojoExecutionException
    {
        getLog().info( message.toString() );
        outputDirectory = project.getBuild().getDirectory();
        androidManifestDirectory = project.getBasedir();

        List<String> classpathElements = null;
        try {
            classpathElements = project.getCompileClasspathElements();
            getLog().debug("Classpath elements: "+classpathElements);
            List<URL> projectClasspathList = new ArrayList<URL>();
            for (String element : classpathElements) {
                try {
                    projectClasspathList.add(new File(element).toURI().toURL());
                    getLog().debug("Classpath element: "+element);
                } catch (MalformedURLException e) {
                    throw new MojoExecutionException(element + " is an invalid classpath element", e);
                }
            };
            URLClassLoader loader = new URLClassLoader(
                    projectClasspathList.toArray(new URL[projectClasspathList.size()]),
                    Thread.currentThread().getContextClassLoader());

            /** 
             * Use the reflections API to find annotated classes. Make sure the URL's and 
             * class loaders are both set. 
             */
            ConfigurationBuilder config = new ConfigurationBuilder();
            config.setUrls(projectClasspathList);
            config.setScanners(new Scanner[]{new SubTypesScanner(), new TypeAnnotationsScanner()});
            config.addClassLoader(loader);
            Reflections reflections = new Reflections(config);

            /**
             * Get activity and service annotations
             */
            Set<Class<?>> annotatedActivityClasses = reflections.getTypesAnnotatedWith(RegisterActivity.class);
            Set<Class<?>> annotatedServiceClasses = reflections.getTypesAnnotatedWith(RegisterService.class);

            /**
             * Prune the existing manifest
             */
            dropActivityAndServiceTagsExistingFromAndroidManifest();

            /**
             * Add the activities and services to the pruned manifest
             */
            addActivityAndServiceTagsToPrunedAndroidManifest(
                    loader,annotatedActivityClasses, annotatedServiceClasses);

        } catch (DependencyResolutionRequiredException e) {
            getLog().error(e);
            new MojoExecutionException("Dependency resolution failed", e);
        } catch (Exception e) {
            getLog().error(e);
            new MojoExecutionException("Generic exception", e);
        }
    }

    private void dropActivityAndServiceTagsExistingFromAndroidManifest() throws Exception
    {
        
        Document doc = FormatXML.formatXML(androidManifestDirectory+"/AndroidManifest.xml");
        
        /** Apply the manifest transform to strip out activity and service tags */
        URL transformFilePath = 
                this.getClass().getClassLoader().getResource("pruneandroidmanifest.xsl");
        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer transformer = tFactory.newTransformer(
                new StreamSource(transformFilePath.openStream()));
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(
                new DOMSource(doc),
                new StreamResult(new FileOutputStream(
                        outputDirectory+"/PrunedAndroidManifest.xml")));
    }

    private void addActivityAndServiceTagsToPrunedAndroidManifest(
            URLClassLoader loader, Set<Class<?>> annotatedActivityClasses, Set<Class<?>> annotatedServiceClasses) throws Exception
            {
        
        Document doc = FormatXML.formatXML(outputDirectory+"/PrunedAndroidManifest.xml"); 

        /** Get the application node from the DOM */
        NodeList nodes = doc.getElementsByTagName("application");
        if (nodes.getLength() != 1)
            throw new IllegalArgumentException("AndroidManifest does not have any application tags.");
        Node applicationNode = nodes.item(0);

        /** Populate the activity elements */
        for (Class<?> annotatedActivityClass: annotatedActivityClasses)
        {
            Class activityClass = loader.loadClass(annotatedActivityClass.getCanonicalName());
            RegisterActivity annotation = 
                    (RegisterActivity) annotatedActivityClass.getAnnotation(RegisterActivity.class);
            
            Element activity = doc.createElement("activity"); 
            activity.setAttribute("android:name", activityClass.getCanonicalName());
            activity.setAttribute("android:enabled", String.valueOf(annotation.enabled()));
            activity.setAttribute("android:screenOrientation", annotation.screenOrientation());
            activity.setAttribute("android:excludeFromRecents", String.valueOf(annotation.excludeFromRecents()));
            if (annotation.launcherActivity())
            {
                Element intentFilter = doc.createElement("intent-filter");
                Element action = doc.createElement("action");
                action.setAttribute("android:name", "android.intent.action.MAIN");
                Element category = doc.createElement("category");
                category.setAttribute("android:name", "android.intent.category.LAUNCHER");  
                intentFilter.appendChild(action);
                intentFilter.appendChild(category);
                activity.appendChild(intentFilter);
            }
            applicationNode.appendChild(activity);
        }
        
        /** Populate the service elements */
        for (Class<?> serviceClass: annotatedServiceClasses)
        {
            RegisterService annotation = (RegisterService) serviceClass.getAnnotation(RegisterService.class);
            
            Element service = doc.createElement("service"); 
            service.setAttribute("android:name", serviceClass.getCanonicalName());
            service.setAttribute("android:enabled", String.valueOf(annotation.enabled()));
            applicationNode.appendChild(service);
        }
        
        /** Overwrite the original manifest */
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        StreamResult result = new StreamResult(new FileWriter(androidManifestDirectory+"/AndroidManifest.xml"));
        DOMSource source = new DOMSource(doc);
        transformer.transform(source, result);
        
        
            }
}
