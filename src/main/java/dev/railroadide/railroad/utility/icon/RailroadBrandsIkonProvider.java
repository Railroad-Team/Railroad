package dev.railroadide.railroad.utility.icon;

import org.kordamp.ikonli.IkonProvider;
import org.kordamp.jipsy.annotations.ServiceProviderFor;

/** Exposes {@link RailroadBrandsIcon} to Ikonli's service-provider discovery. */
@ServiceProviderFor(IkonProvider.class)
public class RailroadBrandsIkonProvider implements IkonProvider<RailroadBrandsIcon> {
    @Override
    public Class<RailroadBrandsIcon> getIkon() {
        return RailroadBrandsIcon.class;
    }
}
