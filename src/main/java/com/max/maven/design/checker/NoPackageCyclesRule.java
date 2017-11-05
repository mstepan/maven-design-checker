package com.max.maven.design.checker;

import jdepend.framework.JDepend;
import jdepend.framework.JavaPackage;
import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


public final class NoPackageCyclesRule implements EnforcerRule {

    private String mainPackage;
    private boolean failOnError;

    @Override
    public void execute(@Nonnull EnforcerRuleHelper helper) throws EnforcerRuleException {

        Log log = helper.getLog();

        try {
            MavenProject project = (MavenProject) helper.evaluate("${project}");
            File targetDir = new File((String) helper.evaluate("${project.build.directory}"));
            File classesDir = new File(targetDir, "classes");

            if ("jar".equalsIgnoreCase(project.getPackaging()) && classesDir.exists()) {
                JDepend jdepend = new JDepend();
                jdepend.addDirectory(classesDir.getAbsolutePath());
                jdepend.analyze();

                if (jdepend.containsCycles()) {

                    List<JavaPackage> projectPackagesOnly =
                            JDependUtil.filter(jdepend.getPackages(), mainPackage, new HashSet<>());

                    log.warn("Total cycles count: " + countCyclesCount(projectPackagesOnly));

                    for (JavaPackage singleJavaPackage : projectPackagesOnly) {

                        if (singleJavaPackage.containsCycle()) {
                            List cycles = new ArrayList();
                            singleJavaPackage.collectCycle(cycles);
                            log.warn(String.format("Cycle detected for '%s': %s",
                                    singleJavaPackage.getName(),
                                    cycles));

                        }
                    }

                    if (failOnError) {
                        throw new EnforcerRuleException("Cycles detected");
                    }
                }
            }
            else {
                log.warn("Skipping jdepend analysis as a '" + classesDir + "' does not exist.");
            }
        }
        catch (ExpressionEvaluationException expEx) {
            throw new EnforcerRuleException("Unable to lookup an expression " + expEx.getLocalizedMessage(), expEx);
        }
        catch (IOException ioEx) {
            throw new EnforcerRuleException("Unable to access target directory " + ioEx.getLocalizedMessage(), ioEx);
        }
    }

    private static int countCyclesCount(List<JavaPackage> packages) {
        int packagesCnt = 0;
        for (JavaPackage singleJavaPackage : packages) {

            if (singleJavaPackage.containsCycle()) {
                ++packagesCnt;
            }
        }

        return packagesCnt;
    }

    @Override
    public boolean isCacheable() {
        return false;
    }

    @Override
    public boolean isResultValid(@Nonnull EnforcerRule enforcerRule) {
        return false;
    }

    @Nullable
    @Override
    public String getCacheId() {
        return null;
    }
}
