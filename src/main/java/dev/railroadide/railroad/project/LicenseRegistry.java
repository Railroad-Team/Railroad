package dev.railroadide.railroad.project;

import dev.railroadide.core.project.License;
import lombok.Getter;

@Getter
public class LicenseRegistry {
    public static final License MIT = register("mit", License.builder()
        .name("MIT License")
        .spdxId("MIT")
        .url("https://opensource.org/licenses/MIT")
        .build());

    public static final License ARR = register("arr", License.builder()
        .name("All Rights Reserved")
        .spdxId("ARR")
        .url("https://choosealicense.com/no-permission/")
        .build());

    public static final License APACHE = register("apache-2.0", License.builder()
        .name("Apache License 2.0")
        .spdxId("Apache-2.0")
        .url("https://www.apache.org/licenses/LICENSE-2.0")
        .build());

    public static final License GPL = register("gpl-3.0", License.builder()
        .name("GNU General Public License v3.0")
        .spdxId("GPL-3.0")
        .url("https://www.gnu.org/licenses/gpl-3.0.en.html")
        .build());

    public static final License LGPL = register("lgpl-3.0", License.builder()
        .name("GNU Lesser General Public License v3.0")
        .spdxId("LGPL-3.0")
        .url("https://www.gnu.org/licenses/lgpl-3.0.en.html")
        .build());

    public static final License BSD = register("bsd-2-clause", License.builder()
        .name("BSD 2-Clause Simplified License")
        .spdxId("BSD-2-Clause")
        .url("https://opensource.org/licenses/BSD-2-Clause")
        .build());

    public static final License BSD3 = register("bsd-3-clause", License.builder()
        .name("BSD 3-Clause New or Revised License")
        .spdxId("BSD-3-Clause")
        .url("https://opensource.org/licenses/BSD-3-Clause")
        .build());

    public static final License CC0 = register("cc0-1.0", License.builder()
        .name("Creative Commons Zero v1.0 Universal")
        .spdxId("CC0-1.0")
        .url("https://creativecommons.org/publicdomain/zero/1.0/")
        .build());

    public static final License CC_BY = register("cc-by-4.0", License.builder()
        .name("Creative Commons Attribution 4.0 International")
        .spdxId("CC-BY-4.0")
        .url("https://creativecommons.org/licenses/by/4.0/")
        .build());

    public static final License ISC = register("isc", License.builder()
        .name("Internet Systems Consortium License")
        .spdxId("ISC")
        .url("https://opensource.org/licenses/ISC")
        .build());

    public static final License MPL = register("mpl-2.0", License.builder()
        .name("Mozilla Public License 2.0")
        .spdxId("MPL-2.0")
        .url("https://www.mozilla.org/en-US/MPL/2.0/")
        .build());

    public static final License UNLICENSE = register("unlicense", License.builder()
        .name("The Unlicense")
        .spdxId("Unlicense")
        .url("https://unlicense.org/")
        .build());

    public static final License WTFPL = register("wtfpl", License.builder()
        .name("Do What The F*ck You Want To Public License")
        .spdxId("WTFPL")
        .url("http://www.wtfpl.net/txt/copying/")
        .build());

    public static final License CUSTOM = register("custom", License.builder()
        .name("Custom License")
        .build());

    public static License register(String id, License license) {
        return License.REGISTRY.register(id, license);
    }
}
