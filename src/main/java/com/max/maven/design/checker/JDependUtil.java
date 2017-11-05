package com.max.maven.design.checker;

import jdepend.framework.JavaPackage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

final class JDependUtil {

    private JDependUtil(){
        throw new IllegalStateException("Utility only class");
    }

    static List<JavaPackage> filter(Collection allPackages, String groupId, Set<String> skipPackagesSet) {
        List<JavaPackage> res = new ArrayList<>();

        for (Object pckg : allPackages) {
            JavaPackage singleJavaPackage = (JavaPackage) pckg;

            // skip not project packages and utility packages
            if (singleJavaPackage.getName().startsWith(groupId) &&
                    !(skipPackagesSet.contains(singleJavaPackage.getName()))) {
                res.add(singleJavaPackage);
            }
        }
        return res;
    }
}
