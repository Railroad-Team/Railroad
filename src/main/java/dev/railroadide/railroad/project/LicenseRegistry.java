package dev.railroadide.railroad.project;

import lombok.Getter;

/**
 * Provides the built-in licenses supported by Railroad.
 * <p>
 * Each constant is registered in {@link License#REGISTRY} when this class is
 * initialized.
 */
@Getter
public class LicenseRegistry {
    /**
     * The MIT License, a permissive license that allows reuse, modification,
     * and distribution provided the copyright notice and license are included.
     */
    public static final License MIT = register("mit", License.builder()
        .name("MIT License")
        .spdxId("MIT")
        .url("https://opensource.org/licenses/MIT")
        .build());

    /**
     * The All Rights Reserved license, which generally permits no reuse,
     * modification, or distribution without the copyright holder's permission.
     */
    public static final License ARR = register("arr", License.builder()
        .name("All Rights Reserved")
        .spdxId("ARR")
        .url("https://choosealicense.com/no-permission/")
        .build());

    /**
     * The Apache License 2.0, a permissive license that includes an express
     * patent grant and requires preservation of notices and attribution.
     */
    public static final License APACHE = register("apache-2.0", License.builder()
        .name("Apache License 2.0")
        .spdxId("Apache-2.0")
        .url("https://www.apache.org/licenses/LICENSE-2.0")
        .build());

    /**
     * The GNU General Public License v3.0, a strong copyleft license requiring
     * distributed modified versions to remain available under the GPL.
     */
    public static final License GPL = register("gpl-3.0", License.builder()
        .name("GNU General Public License v3.0")
        .spdxId("GPL-3.0")
        .url("https://www.gnu.org/licenses/gpl-3.0.en.html")
        .build());

    /**
     * The GNU Lesser General Public License v3.0, a library-focused copyleft
     * license that permits proprietary applications to link to the library.
     */
    public static final License LGPL = register("lgpl-3.0", License.builder()
        .name("GNU Lesser General Public License v3.0")
        .spdxId("LGPL-3.0")
        .url("https://www.gnu.org/licenses/lgpl-3.0.en.html")
        .build());

    /**
     * The BSD 2-Clause Simplified License, a permissive license requiring the
     * copyright notice and disclaimer to be retained in distributions.
     */
    public static final License BSD = register("bsd-2-clause", License.builder()
        .name("BSD 2-Clause Simplified License")
        .spdxId("BSD-2-Clause")
        .url("https://opensource.org/licenses/BSD-2-Clause")
        .build());

    /**
     * The BSD 3-Clause New or Revised License, a permissive license like the
     * BSD 2-Clause license that also restricts using contributors' names for
     * endorsement.
     */
    public static final License BSD3 = register("bsd-3-clause", License.builder()
        .name("BSD 3-Clause New or Revised License")
        .spdxId("BSD-3-Clause")
        .url("https://opensource.org/licenses/BSD-3-Clause")
        .build());

    /**
     * The Creative Commons Zero v1.0 Universal dedication, which waives rights
     * as far as legally possible to place a work in the public domain.
     */
    public static final License CC0 = register("cc0-1.0", License.builder()
        .name("Creative Commons Zero v1.0 Universal")
        .spdxId("CC0-1.0")
        .url("https://creativecommons.org/publicdomain/zero/1.0/")
        .build());

    /**
     * The Creative Commons Attribution 4.0 International license, which allows
     * sharing and adaptation provided appropriate credit is given.
     */
    public static final License CC_BY = register("cc-by-4.0", License.builder()
        .name("Creative Commons Attribution 4.0 International")
        .spdxId("CC-BY-4.0")
        .url("https://creativecommons.org/licenses/by/4.0/")
        .build());

    /**
     * The Internet Systems Consortium License, a short permissive license that
     * allows reuse and modification with the copyright notice and disclaimer.
     */
    public static final License ISC = register("isc", License.builder()
        .name("Internet Systems Consortium License")
        .spdxId("ISC")
        .url("https://opensource.org/licenses/ISC")
        .build());

    /**
     * The Mozilla Public License 2.0, a file-level copyleft license that allows
     * covered code to be combined with code under other licenses.
     */
    public static final License MPL = register("mpl-2.0", License.builder()
        .name("Mozilla Public License 2.0")
        .spdxId("MPL-2.0")
        .url("https://www.mozilla.org/en-US/MPL/2.0/")
        .build());

    /**
     * The Unlicense, a public-domain dedication intended to let anyone use,
     * modify, and distribute the work without restriction.
     */
    public static final License UNLICENSE = register("unlicense", License.builder()
        .name("The Unlicense")
        .spdxId("Unlicense")
        .url("https://unlicense.org/")
        .build());

    /**
     * The Do What The F*ck You Want To Public License, an extremely permissive
     * license intended to allow nearly unrestricted use and redistribution.
     */
    public static final License WTFPL = register("wtfpl", License.builder()
        .name("Do What The F*ck You Want To Public License")
        .spdxId("WTFPL")
        .url("http://www.wtfpl.net/txt/copying/")
        .build());

    /**
     * A placeholder license for projects using a custom license whose terms are
     * defined separately by the project author.
     */
    public static final License CUSTOM = register("custom", License.builder()
        .name("Custom License")
        .build());

    /**
     * Ensures that the built-in licenses are initialized and registered.
     * <p>
     * Calling this method has no effect after class initialization.
     */
    public static void initialize() {
        // Intentionally left blank.
    }

    /**
     * Registers a license under the specified registry identifier.
     *
     * @param id the identifier to associate with the license
     * @param license the license to register
     * @return the registered license
     */
    public static License register(String id, License license) {
        return License.REGISTRY.register(id, license);
    }
}
