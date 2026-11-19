/**
 * Plugin SPI for language contributions, typed capabilities, owned project sessions and provider execution.
 * Contributions declare immutable namespaced identities and optional contracts. Sessions own their service
 * instances, link requests to their cancellation lifetime and reject retained handles after unload.
 * Syntax, semantics, diagnostics and index features use the same captured request and result identities.
 * Java implementation types and editor controls are outside this package's public boundary.
 *
 * <p>
 * Language-neutral execution and health observations for text and binary providers.
 * Hosts submit captured {@link dev.railroadide.railroad.ide.sst.request.api.RequestContext} inputs through
 * {@link dev.railroadide.railroad.ide.sst.provider.api.ProviderExecutor}, using stable provider identities
 * rather than document or version-specific identities. Scheduling priority and supersession remain the
 * responsibility of the shared request scheduler.
 *
 * <p>
 * The application executor admits at most 64 executions, eight per provider, and retains at most 128
 * least-recently-used provider aggregates. Idle aggregates can be evicted, while active provider entries
 * remain pinned. A 250 millisecond completed execution is considered slow. Measurements use constant
 * space per provider and monotonic timing at execution boundaries; health queries allocate snapshots
 * on demand rather than retaining an invocation history or document contents.
 *
 * <p>
 * Hosts choose a finite budget appropriate to their feature. Java inspections receive five seconds
 * per provider, bounded by the outer request. Child callbacks observe their own budget and parent
 * cancellation/deadline through the common activity check. Independent failure, cancellation, and
 * timeout counters allow hosts to present health diagnostics without logging expected cancellation
 * as a plugin error. Failed rules and timed-out providers do not publish partial diagnostics.
 *
 * <p>
 * A timeout bounds caller waiting, not arbitrary plugin execution. Interrupt-ignoring code continues
 * to occupy its admission slot until it exits; once its slots are occupied, new calls are rejected
 * rather than spawning more abandoned workers. Providers must avoid external side effects, poll
 * request controls in expensive loops, and return immutable results. Hosts validate captured versions
 * before publication. Closing is nonblocking and cancels current calls; it cannot forcibly terminate
 * uncooperative JVM code. The application owns the shared instance; tests and independent hosts own
 * and close explicitly constructed instances.
 */
package dev.railroadide.railroad.ide.sst.provider.api;
