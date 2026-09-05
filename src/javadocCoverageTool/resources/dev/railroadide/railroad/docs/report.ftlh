<#ftl output_format="HTML" auto_esc=true>
<#macro badge coverage>
    <span class="badge">${coverage.complete}/${coverage.total} complete · ${coverage.percentage}</span>
</#macro>
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Railroad Javadoc coverage</title>
    <#-- Include trusted static assets verbatim so the report remains a single offline file. -->
    <style><#include "report.css" parse=false></style>
</head>
<body>
<header id="top">
    <p class="eyebrow">RAILROAD / CODE QUALITY</p>
    <h1>Javadoc coverage</h1>
    <p>Public API documentation, organized by package, class, and member.</p>
    <div class="stats">
        <div><strong>${percentage}</strong>coverage</div>
        <div><strong>${complete} / ${total}</strong>complete declarations</div>
        <div><strong>${incomplete}</strong>need documentation</div>
        <div><strong>${typeCount}</strong>public types</div>
    </div>
</header>
<main>
    <details class="rules">
        <summary>Coverage rules and scope</summary>
        <p>Main Java sources only. Public classes, interfaces, enums, records, and annotation types are checked,
            including publicly accessible nested types. Each type, declared public method, explicit public constructor,
            and public static final field (including interface and enum constants) needs a nonempty Javadoc description.</p>
        <p>Methods and constructors need a nonempty @param for every value and type parameter. Non-void methods
            also need a nonempty @return (block or inline). Type parameters and record components need @param tags on the type.
            Void methods and constructors do not need @return. Coverage counts a declaration as complete only when all its checks pass.</p>
        <p>Only declarations written in source are counted: generated methods (including Lombok and implicit record/enum members),
            inherited members, and members inside inaccessible types are excluded. Overrides require explicit documentation;
            {@inheritDoc} is reported as unverified. This checks documentation presence, not its accuracy or full Javadoc validity.
            An empty scope has N/A coverage.</p>
    </details>
    <div class="toolbar">
        <label>Search <input id="search" type="search" placeholder="Package, class, or member"></label>
        <label><input id="incomplete" type="checkbox"> Incomplete only</label>
        <button id="expand" type="button">Expand all</button>
        <button id="collapse" type="button">Collapse all</button>
        <span id="visible-count" role="status" aria-live="polite"></span>
    </div>
    <noscript><p>Enable JavaScript for search and filters. Package and class sections can still be opened below.</p></noscript>
    <nav aria-label="Packages">
        <h2>Packages</h2>
        <div class="package-links">
            <#list packages as package>
                <a href="#${package.id}">${package.name} <span>${package.types?size}</span></a>
            </#list>
        </div>
    </nav>
    <p id="no-results" hidden>No matching declarations.</p>
    <#list packages as package>
        <details class="package" id="${package.id}">
            <summary>${package.name}<@badge coverage=package.coverage /></summary>
            <#list package.types as type>
                <details class="type" id="${type.id}">
                    <summary>${type.name}<@badge coverage=type.coverage /></summary>
                    <div class="table-wrap">
                        <table>
                            <thead>
                            <tr>
                                <th scope="col">Declaration</th>
                                <th scope="col">Status / missing documentation</th>
                                <th scope="col">Source</th>
                            </tr>
                            </thead>
                            <tbody>
                            <#list type.entries as entry>
                                <tr id="${entry.id}" data-complete="${entry.complete?c}" data-search="${entry.search}">
                                    <td>
                                        <span class="kind">${entry.kind}</span>
                                        <a href="#${entry.id}"><code>${entry.name}</code></a>
                                    </td>
                                    <td>
                                        <#if entry.complete>
                                            <span class="pass">Complete</span>
                                        <#else>
                                            <span class="fail">Incomplete</span>
                                            <ul>
                                                <#list entry.issues as issue>
                                                    <li>${issue}</li>
                                                </#list>
                                            </ul>
                                        </#if>
                                    </td>
                                    <td class="source">${entry.source}:${entry.line}</td>
                                </tr>
                            </#list>
                            </tbody>
                        </table>
                    </div>
                </details>
            </#list>
        </details>
    </#list>
    <#if total == 0>
        <p>No public API declarations found in the configured main Java sources.</p>
    </#if>
</main>
<footer><a href="#top">Back to top</a> · Generated by javadocCoverage</footer>
<script><#include "report.js" parse=false></script>
</body>
</html>
