package uk.sawcz.calendarchecker;

import microsoft.exchange.webservices.data.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by sawczc01 on 06/02/2015.
 */
public class PullNotificationChecker implements Runnable
{
    public interface Listener
    {
        void itemsCreated();
    }

    private final ExchangeService service;
    private final Listener listener;
    private PullSubscription pullSubscription;

    public PullNotificationChecker(final ExchangeService service, Listener listener)
    {
        this.service = service;
        this.listener = listener;
    }

    @Override
    public void run()
    {
        WellKnownFolderName wkFolder = WellKnownFolderName.Calendar;
        FolderId folderId = new FolderId(wkFolder);
        List<FolderId> folder = new ArrayList<FolderId>();
        folder.add(folderId);
        if (pullSubscription == null)
        {
            try
            {
                pullSubscription = service.subscribeToPullNotifications(folder, 1, null, EventType.NewMail, EventType.Created);
            }
            catch (Exception e)
            {
                e.printStackTrace();
                return;
            }
        }

        GetEventsResults events = null;
        try
        {
            events = pullSubscription.getEvents();
            if (events.getItemEvents().iterator().hasNext())
            {
                listener.itemsCreated();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return;
        }
    }
}
