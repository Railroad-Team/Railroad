package dev.railroadide.railroad.project;

import lombok.Getter;

@Getter
public enum License {
    MIT("MIT License", "MIT"),
    ARR("All Rights Reserved", "All Rights Reserved"),
    APACHE("Apache License 2.0", "Apache-2.0"),
    GPL("GNU General Public License v3.0", "GPL-3.0"),
    LGPL("GNU Lesser General Public License v3.0", "LGPL-3.0"),
    BSD("BSD 2-Clause Simplified License", "BSD-2-Clause"),
    BSD3("BSD 3-Clause New or Revised License", "BSD-3-Clause"),
    CC0("Creative Commons Zero v1.0 Universal", "CC0-1.0"),
    CC_BY("Creative Commons Attribution 4.0 International", "CC-BY-4.0"),
    ISC("Internet Systems Consortium License", "ISC"),
    MPL("Mozilla Public License 2.0", "MPL-2.0"),
    UNLICENSE("The Unlicense", "Unlicense"),
    WTFPL("Do What The F*ck You Want To Public License", "WTFPL"),
    CUSTOM("Custom License", "Custom");

    private final String name;
    private final String spdxId;

    License(String name, String spdxId) {
        this.name = name;
        this.spdxId = spdxId;
    }

    public static License fromSpdxId(String spdxId) {
        for (License license : values()) {
            if (license.spdxId.equals(spdxId)) {
                return license;
            }
        }

        return null;
    }

    public static License fromName(String name) {
        for (License license : values()) {
            if (license.name.equals(name)) {
                return license;
            }
        }

        return null;
    }
}
