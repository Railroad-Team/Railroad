package dev.railroadide.railroad.utility.icon;

import org.kordamp.ikonli.IkonProvider;
import org.kordamp.jipsy.annotations.ServiceProviderFor;

@ServiceProviderFor(IkonProvider.class)
public class RailroadIkonProvider implements IkonProvider<RailroadIcon> {
    @Override
    public Class<RailroadIcon> getIkon() {
        return RailroadIcon.class;
    }
}
