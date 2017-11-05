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
import java.util.HashSet;
import java.util.List;


public final class StableDependencyRule implements EnforcerRule {

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

                List<JavaPackage> projectPackagesOnly =
                        JDependUtil.filter(jdepend.getPackages(), mainPackage, new HashSet<>());

                for (JavaPackage singlePackage : projectPackagesOnly) {
                    for (JavaPackage usedPackage : JDependUtil.filter(singlePackage.getEfferents(), mainPackage,
                            new HashSet<>())) {

                        if (Float.compare(usedPackage.instability(), singlePackage.instability()) > 0) {

                            String errorMsg = String.format(
                                    "Stable Dep. violated: '%s'[%.2f] -> '%s'[%.2f]",
                                    singlePackage.getName(),
                                    singlePackage.instability(),
                                    usedPackage.getName(),
                                    usedPackage.instability());

                            if (failOnError) {
                                throw new EnforcerRuleException(errorMsg);
                            }
                            else {

                                log.warn(errorMsg);
                            }
                        }
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
