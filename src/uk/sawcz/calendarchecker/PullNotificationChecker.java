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
        void itemsCreated(List<Appointment> appointments);
    }

    public static final int TIMEOUT_MINUTES = 1;

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
                pullSubscription = service.subscribeToPullNotifications(folder, TIMEOUT_MINUTES, null, EventType.NewMail, EventType.Created);
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
            List<Appointment> appointments = new ArrayList<Appointment>();
            for(ItemEvent event : events.getItemEvents())
            {
                appointments.add(Appointment.bind(service, event.getItemId()));
            }
            listener.itemsCreated(appointments);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return;
        }
    }
}
