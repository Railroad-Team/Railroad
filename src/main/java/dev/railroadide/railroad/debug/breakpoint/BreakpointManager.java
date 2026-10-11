package dev.railroadide.railroad.debug.breakpoint;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import dev.railroadide.railroad.debug.source.SourceResolver;

import java.util.*;

/**
 * Binds source breakpoints to JDI locations and tracks requests for classes prepared later.
 */
public final class BreakpointManager {
    private static final String PROPERTY_BREAKPOINT_ID = "railroad.breakpoint.id";

    private final VirtualMachine vm;
    private final SourceResolver sourceResolver;

    private final Map<UUID, RuntimeBreakpoint> breakpoints = new HashMap<>();

    /**
     * Creates a runtime request manager for one target virtual machine.
     *
     * @param vm target virtual machine whose requests are managed
     * @param sourceResolver mapping from runtime locations to source content
     */
    public BreakpointManager(VirtualMachine vm, SourceResolver sourceResolver) {
        this.vm = vm;
        this.sourceResolver = sourceResolver;
    }

    /**
     * Tracks a breakpoint and binds enabled requests to prepared classes and future class-prepare events.
     *
     * @param breakpoint source breakpoint definition to install
     */
    public void add(SourceBreakpoint breakpoint) {
        var runtime = new RuntimeBreakpoint(breakpoint);
        breakpoints.put(breakpoint.id(), runtime);
        if (!breakpoint.enabled())
            return;

        installPrepareRequest(runtime);

        for (ReferenceType type : vm.allClasses()) {
            if (type.isPrepared()) {
                tryBind(runtime, type);
            }
        }
    }

    private void installPrepareRequest(RuntimeBreakpoint breakpoint) {
        ClassPrepareRequest request = vm.eventRequestManager().createClassPrepareRequest();
        request.putProperty(PROPERTY_BREAKPOINT_ID, breakpoint.definition.id());

        Optional<String> sourceName = sourceResolver.sourceName(breakpoint.definition.source());
        if (vm.canUseSourceNameFilters() && sourceName.isPresent()) {
            request.addSourceNameFilter(sourceName.get());
        } else {
            Optional<String> classPattern = sourceResolver.classPatternFor(breakpoint.definition.source());
            if (classPattern.isEmpty())
                /*
                 * Better to leave it pending than install an
                 * unfiltered SUSPEND_ALL ClassPrepareRequest
                 * for every class loaded by the JVM.
                 */
                return;

            request.addClassFilter(classPattern.get());
        }

        request.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        request.enable();
        breakpoint.prepareRequests.add(request);
    }

    /**
     * Binds the breakpoint associated with a class-prepare request to the newly prepared type.
     *
     * @param event class-prepare event carrying the breakpoint request identifier
     */
    public void onClassPrepared(ClassPrepareEvent event) {
        Object id = event.request().getProperty(PROPERTY_BREAKPOINT_ID);
        if (!(id instanceof UUID breakpointId))
            return;

        RuntimeBreakpoint breakpoint = breakpoints.get(breakpointId);
        if (breakpoint == null)
            return;

        tryBind(
            breakpoint,
            event.referenceType());
    }

    /**
     * Removes a breakpoint and deletes its installed breakpoint and class-prepare requests.
     * Unknown identifiers are ignored.
     *
     * @param breakpointId stable identifier of the breakpoint to remove
     */
    public void remove(UUID breakpointId) {
        RuntimeBreakpoint breakpoint = breakpoints.remove(breakpointId);
        if (breakpoint == null)
            return;

        EventRequestManager manager = vm.eventRequestManager();
        if (!breakpoint.requests.isEmpty()) {
            manager.deleteEventRequests(breakpoint.requests);
        }

        if (!breakpoint.prepareRequests.isEmpty()) {
            manager.deleteEventRequests(breakpoint.prepareRequests);
        }
    }

    private void tryBind(RuntimeBreakpoint breakpoint, ReferenceType type) {
        SourceBreakpoint definition = breakpoint.definition;

        try {
            List<Location> locations = type.locationsOfLine(definition.line());
            for (Location location : locations) {
                if (!sourceResolver.matches(definition.source(), location))
                    continue;

                if (!breakpoint.boundLocations.add(location))
                    continue;

                BreakpointRequest request = vm.eventRequestManager().createBreakpointRequest(location);
                request.putProperty(PROPERTY_BREAKPOINT_ID, definition.id());
                request.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                request.enable();
                breakpoint.requests.add(request);
            }
        } catch (AbsentInformationException _) {
            /*
             * Class was not compiled with usable line info.
             * Leave breakpoint unbound.
             */
        }
    }

    private static final class RuntimeBreakpoint {
        private SourceBreakpoint definition;

        private final List<BreakpointRequest> requests = new ArrayList<>();
        private final List<ClassPrepareRequest> prepareRequests = new ArrayList<>();
        private final Set<Location> boundLocations = new HashSet<>();

        private RuntimeBreakpoint(SourceBreakpoint definition) {
            this.definition = definition;
        }
    }
}
