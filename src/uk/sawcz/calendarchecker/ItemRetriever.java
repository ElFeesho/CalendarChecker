package uk.sawcz.calendarchecker;

import microsoft.exchange.webservices.data.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by sawczc01 on 06/02/2015.
 */
public class ItemRetriever implements Runnable
{
    private Date lastDate = null;

    public interface Listener
    {
        public void itemsRetrieved(List<Appointment> items);
    }

    private final ExchangeService service;
    private final Listener listener;

    public ItemRetriever(ExchangeService service, Listener listener)
    {
        this.service = service;
        this.listener = listener;
    }

    @Override
    public void run()
    {
        try
        {
            if (lastDate == null)
            {
                lastDate = new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10));
            }
            System.out.println("Checking for meetings from "+lastDate);

            CalendarView calendarView = new CalendarView(lastDate, new Date(lastDate.getTime() + TimeUnit.DAYS.toMillis(20)), 50);

            FindItemsResults<Appointment> appointments = service.findAppointments(WellKnownFolderName.Calendar, calendarView);

            if (appointments.getItems().size() > 0)
            {
                lastDate = calendarView.getEndDate();

                System.out.println("Found items: " + appointments.getItems().size());

                List<Appointment> items = new ArrayList<Appointment>(appointments.getItems());
                for (int i = appointments.getItems().size() - 1; i > 0; i--)
                {
                    Appointment appointment = appointments.getItems().get(i);
                    MeetingResponseType myResponseType = appointment.getMyResponseType();

                    if(myResponseType == MeetingResponseType.Organizer || (myResponseType != MeetingResponseType.NoResponseReceived && myResponseType != MeetingResponseType.Unknown))
                    {
                        System.out.println("Discarding appointment organised or responded to");
                        items.remove(i);
                    }
                }
                listener.itemsRetrieved(items);
            }


        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
