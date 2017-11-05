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
import java.util.*;


public final class DistanceFromMainSequenceRule implements EnforcerRule {

    private double threshold = 0.5;
    private String mainPackage;
    private String skipPackages;
    private boolean failOnError;

    @Override
    public void execute(@Nonnull EnforcerRuleHelper helper) throws EnforcerRuleException {

        Set<String> skipPackagesSet = createSkippedPackagesSet(skipPackages);

        Log log = helper.getLog();

        try {
            final MavenProject project = (MavenProject) helper.evaluate("${project}");
            final File targetDir = new File((String) helper.evaluate("${project.build.directory}"));
            final File classesDir = new File(targetDir, "classes");

            if ("jar".equalsIgnoreCase(project.getPackaging()) && classesDir.exists()) {
                JDepend jdepend = new JDepend();
                jdepend.addDirectory(classesDir.getAbsolutePath());
                jdepend.analyze();

                List<JavaPackage> projectPackagesOnly =
                        JDependUtil.filter(jdepend.getPackages(), mainPackage, skipPackagesSet);

                log.info(String.format("standardDeviation: %.2f, threshold: %.2f",
                        distanceStandardDeviation(projectPackagesOnly),
                        threshold));

                for (JavaPackage singleJavaPackage : projectPackagesOnly) {
                    double distanceFromMain = singleJavaPackage.distance();

                    // distance is greater than 2 standard deviations
                    if (Double.compare(distanceFromMain, threshold) >= 0) {

                        String problemDesc = describePackageProblem(singleJavaPackage);

                        if (failOnError) {
                            throw new EnforcerRuleException(problemDesc);
                        }
                        else {
                            log.warn(problemDesc);
                        }
                    }
                }
            }
            else {
                log.warn("Skipping JDepend analysis as a '" + classesDir + "' does not exist.");
            }
        }
        catch (ExpressionEvaluationException expEx) {
            throw new EnforcerRuleException("Unable to lookup an expression " + expEx.getLocalizedMessage(), expEx);
        }
        catch (IOException ioEx) {
            throw new EnforcerRuleException("Unable to access target directory " + ioEx.getLocalizedMessage(), ioEx);
        }
    }

    private static Set<String> createSkippedPackagesSet(String skipPackages) {

        if (skipPackages == null) {
            return Collections.emptySet();
        }

        String[] skipPackagesArr = skipPackages.trim().split(",");

        Set<String> skipPackagesSet = new HashSet<>();

        for (String packageToSkip : skipPackagesArr) {
            skipPackagesSet.add(packageToSkip.trim());
        }

        return skipPackagesSet;
    }



    private static double distanceStandardDeviation(List<JavaPackage> packagesList) {

        final int n = packagesList.size();

        double sum = 0.0;

        for (JavaPackage singleJavaPackage : packagesList) {
            sum += singleJavaPackage.distance();
        }

        double avg = sum / n;

        double deviation = 0.0;

        for (JavaPackage singleJavaPackage : packagesList) {
            double diff = (singleJavaPackage.distance() - avg);
            deviation += (diff * diff);
        }

        return Math.sqrt(deviation / n);
    }

    private static String describePackageProblem(JavaPackage singleJavaPackage) {
        if (Float.compare(singleJavaPackage.abstractness(), 0.5F) >= 0) {

            return String.format("'%s', is too abstract [%d/%d] and is not used a lot [%d] [just remove]" +
                            "[D = %.2f, A = %.2f, I = %.2f].",
                    singleJavaPackage.getName(),
                    singleJavaPackage.getAbstractClassCount(),
                    singleJavaPackage.getClassCount(),
                    singleJavaPackage.afferentCoupling(),
                    singleJavaPackage.distance(),
                    singleJavaPackage.abstractness(),
                    singleJavaPackage.instability());
        }

        return String.format("'%s', is very concrete [%d/%d] and lot's of packages depends on it [%d] " +
                        "[make more abstract or reduce dependants]" +
                        "[D = %.2f, A = %.2f, I = %.2f].",
                singleJavaPackage.getName(),
                singleJavaPackage.getConcreteClassCount(),
                singleJavaPackage.getClassCount(),
                singleJavaPackage.afferentCoupling(),
                singleJavaPackage.distance(),
                singleJavaPackage.abstractness(),
                singleJavaPackage.instability());
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
