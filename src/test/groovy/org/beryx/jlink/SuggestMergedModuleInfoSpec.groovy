/*
 * Copyright 2018 the original author or authors.
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
package org.beryx.jlink

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Path
import java.util.stream.Collectors

class SuggestMergedModuleInfoSpec extends Specification {
    @TempDir Path testProjectDir

    static Set<String> GROOVY_DIRECTIVES_CONSTRAINT = [
            "requires 'java.management';",
            "requires 'java.naming';",
            "requires 'java.logging';",
            "requires 'java.scripting';",
            "requires 'java.sql';",
            "requires 'java.xml';",
            "requires 'java.desktop';",
            "requires 'java.datatransfer';",
            "provides 'javax.annotation.processing.Processor' with 'org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor';",
            "provides 'javax.imageio.spi.ImageWriterSpi' with 'com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageWriterSpi', 'com.twelvemonkeys.imageio.plugins.tiff.TIFFImageWriterSpi';",
            "provides 'javax.imageio.spi.ImageReaderSpi' with 'com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageReaderSpi', 'com.twelvemonkeys.imageio.plugins.tiff.BigTIFFImageReaderSpi', 'com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReaderSpi';",
    ]

    static Set<String> GROOVY_DIRECTIVES = GROOVY_DIRECTIVES_CONSTRAINT + [
            "requires 'java.rmi';",
            "requires 'java.compiler';",
            "provides 'org.apache.logging.log4j.spi.Provider' with 'org.apache.logging.log4j.core.impl.Log4jProvider';",
            "provides 'org.apache.logging.log4j.message.ThreadDumpMessage.ThreadInfoFactory' with 'org.apache.logging.log4j.core.message.ExtendedThreadInfoFactory';",
            "provides 'org.apache.logging.log4j.core.util.ContextDataProvider' with 'org.apache.logging.log4j.core.impl.ThreadContextDataProvider';",
    ]

    static Set<String> KOTLIN_DIRECTIVES_CONSTRAINT = [
            'requires("java.datatransfer");',
            'requires("java.management");',
            'requires("java.naming");',
            'requires("java.logging");',
            'requires("java.scripting");',
            'requires("java.sql");',
            'requires("java.xml");',
            'requires("java.desktop");',
            'provides("javax.annotation.processing.Processor").with("org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor");',
            'provides("javax.imageio.spi.ImageReaderSpi").with("com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageReaderSpi", "com.twelvemonkeys.imageio.plugins.tiff.BigTIFFImageReaderSpi", "com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReaderSpi");',
            'provides("javax.imageio.spi.ImageWriterSpi").with("com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageWriterSpi", "com.twelvemonkeys.imageio.plugins.tiff.TIFFImageWriterSpi");',
    ]
    static Set<String> KOTLIN_DIRECTIVES = KOTLIN_DIRECTIVES_CONSTRAINT + [
            'requires("java.rmi");',
            'requires("java.compiler");',
            'provides("org.apache.logging.log4j.spi.Provider").with("org.apache.logging.log4j.core.impl.Log4jProvider");',
            'provides("org.apache.logging.log4j.message.ThreadDumpMessage.ThreadInfoFactory").with("org.apache.logging.log4j.core.message.ExtendedThreadInfoFactory");',
            'provides("org.apache.logging.log4j.core.util.ContextDataProvider").with("org.apache.logging.log4j.core.impl.ThreadContextDataProvider");',
    ]

    static Set<String> JAVA_DIRECTIVES_CONSTRAINT = [
            "requires java.sql;",
            "requires java.naming;",
            "requires java.desktop;",
            "requires java.logging;",
            "requires java.scripting;",
            "requires java.xml;",
            "requires java.datatransfer;",
            "requires java.management;",
            "provides javax.annotation.processing.Processor with org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor;",
            "provides javax.imageio.spi.ImageReaderSpi with com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageReaderSpi, com.twelvemonkeys.imageio.plugins.tiff.BigTIFFImageReaderSpi, com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReaderSpi;",
            "provides javax.imageio.spi.ImageWriterSpi with com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageWriterSpi, com.twelvemonkeys.imageio.plugins.tiff.TIFFImageWriterSpi;",
    ]
    static Set<String> JAVA_DIRECTIVES = JAVA_DIRECTIVES_CONSTRAINT + [
            "requires java.rmi;",
            "requires java.compiler;",
            "provides org.apache.logging.log4j.spi.Provider with org.apache.logging.log4j.core.impl.Log4jProvider;",
            "provides org.apache.logging.log4j.message.ThreadDumpMessage.ThreadInfoFactory with org.apache.logging.log4j.core.message.ExtendedThreadInfoFactory;",
            "provides org.apache.logging.log4j.core.util.ContextDataProvider with org.apache.logging.log4j.core.impl.ThreadContextDataProvider;",
    ]


    def cleanup() {
        println "CLEANUP"
    }

    @Unroll
    def "should display the correct module-info for the merged module in #gradleFile with #language flavor using Gradle #gradleVersion"() {
        given:
        new AntBuilder().copy( todir: testProjectDir ) {
            fileset( dir: 'src/test/resources/hello-log4j-2.19.0' )
        }
        File buildFile = new File(testProjectDir.toFile(), gradleFile)
        def outputWriter = new StringWriter(8192)

        when:
        BuildResult result = GradleRunner.create()
                .withDebug(debug)
                .withGradleVersion(gradleVersion)
                .forwardStdOutput(outputWriter)
                .withProjectDir(buildFile.parentFile)
                .withPluginClasspath()
                .withArguments("-is", JlinkPlugin.TASK_NAME_SUGGEST_MERGED_MODULE_INFO, '-b', gradleFile, "--useConstraints", "--language=$language")
                .build();
        def task = result.task(":$JlinkPlugin.TASK_NAME_SUGGEST_MERGED_MODULE_INFO")
        println outputWriter

        then:
        task.outcome == TaskOutcome.SUCCESS

        when:
        def taskOutput = outputWriter.toString()
        def directives = getDirectives(taskOutput, language)

        then:
        directives.size() == expectedDirectives.size()
        directives as Set == expectedDirectives

        where:
        language | expectedDirectives           | gradleFile                  | gradleVersion | debug
        'groovy' | GROOVY_DIRECTIVES            | 'build.gradle'              | '7.0'         | true
        'kotlin' | KOTLIN_DIRECTIVES            | 'build.gradle'              | '7.6'         | true
        'java'   | JAVA_DIRECTIVES              | 'build.gradle'              | '7.2'         | true
        'groovy' | GROOVY_DIRECTIVES_CONSTRAINT | 'build.additive.gradle'     | '7.6'         | true
        'kotlin' | KOTLIN_DIRECTIVES_CONSTRAINT | 'build.additive.gradle'     | '7.0'         | true
        'java'   | JAVA_DIRECTIVES_CONSTRAINT   | 'build.additive.gradle'     | '7.6'         | true
        'groovy' | GROOVY_DIRECTIVES_CONSTRAINT | 'build.additive.gradle.kts' | '7.2'         | false
        'kotlin' | KOTLIN_DIRECTIVES_CONSTRAINT | 'build.additive.gradle.kts' | '7.6'         | false
        'java'   | JAVA_DIRECTIVES_CONSTRAINT   | 'build.additive.gradle.kts' | '7.6'         | false
    }

    List<String> getDirectives(String taskOutput, String language) {
        def blockStart = 'mergedModule {'
        def blockEnd = '}'

        int startPos = taskOutput.indexOf(blockStart)
        assert startPos >= 0
        startPos += blockStart.length()
        int endPos = taskOutput.indexOf(blockEnd, startPos)
        assert endPos >= 0
        def content = taskOutput.substring(startPos, endPos)
        content = content.lines().map{it.trim()}.filter{!it.empty}.collect(Collectors.joining('\n'))
        content = content.replace(',\n', ', ')
        content.lines().collect(Collectors.toList())
    }
}
