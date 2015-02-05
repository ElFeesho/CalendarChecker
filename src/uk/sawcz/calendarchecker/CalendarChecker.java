package uk.sawcz.calendarchecker;

import microsoft.exchange.webservices.data.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class CalendarChecker
{
    public interface Listener
    {
        public void newCalendarEventCreated(List<Appointment> appointment);
    }

    private final ExecutorService threadService = Executors.newSingleThreadExecutor();
    private final ExecutorService concurrentService = Executors.newFixedThreadPool(3);
    private final Listener listener;

    public static class AppointmentStore
    {
        public interface Listener
        {
            public void newAppointmentsAvailable(List<Appointment> appointments);
        }

        private final AppointmentStore.Listener listener;

        private List<Appointment> knownAppointments = new ArrayList<Appointment>();

        public AppointmentStore(AppointmentStore.Listener listener)
        {
            this.listener = listener;
        }

        public void storeAppointments(List<Appointment> appointments) throws ServiceLocalException
        {
            List<Appointment> foundAppointments = new ArrayList<Appointment>();
            for(Appointment potentialNewAppointment : appointments)
            {
                for (Appointment knownAppointment : knownAppointments)
                {
                    if(knownAppointment.getId().equals(potentialNewAppointment.getId()))
                    {
                        foundAppointments.add(potentialNewAppointment);
                    }
                }
            }

            List<Appointment> newAppointments = new ArrayList<Appointment>();
            if(foundAppointments.size() != appointments.size())
            {
                for(Appointment appointment : appointments)
                {
                    boolean found = false;
                    for(Appointment foundAppointment : foundAppointments)
                    {
                        if(foundAppointment.getId().equals(appointment.getId()))
                        {
                            found = true;
                            break;
                        }
                    }
                    if(!found)
                    {
                        newAppointments.add(appointment);
                    }
                }
            }
            if(newAppointments.size()>0)
            {
                listener.newAppointmentsAvailable(newAppointments);
                knownAppointments.addAll(newAppointments);
            }

        }
    }

    public static class PullNotificationChecker implements Runnable
    {
        public interface Listener
        {
            void itemsCreated();
        }

        private final ExchangeService service;
        private final PullNotificationChecker.Listener listener;

        public PullNotificationChecker(ExchangeService service, PullNotificationChecker.Listener listener)
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
            PullSubscription subscription = null;
            while(true)
            {
                try
                {
                    subscription = service.subscribeToPullNotifications(folder, 1, null, EventType.NewMail, EventType.Created);
                    while (true)
                    {
                        GetEventsResults events = subscription.getEvents();
                        boolean found = false;
                        for (NotificationEvent event : events.getAllEvents())
                        {
                            System.out.println("EVENT: " + event.getEventType() + " " + event.getTimestamp());
                            found = true;
                        }
                        if (found)
                        {
                            listener.itemsCreated();
                        }
                        Thread.sleep(60000);
                    }

                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class ItemRetriever implements Runnable
    {
        public interface Listener
        {
            public void itemsRetrieved(List<Item> items);
        }

        private final ExchangeService service;
        private final ItemRetriever.Listener listener;

        public ItemRetriever(ExchangeService service, ItemRetriever.Listener listener)
        {
            this.service = service;
            this.listener = listener;
        }

        @Override
        public void run()
        {
            try
            {
                ItemView itemView = new ItemView(25);
                itemView.getOrderBy().add(ItemSchema.DateTimeReceived, SortDirection.Descending);

                FindItemsResults<Item> items = service.findItems(WellKnownFolderName.Calendar, itemView);
                System.out.println("Found items: " + items.getItems().size());
                listener.itemsRetrieved(items.getItems());
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    private ExchangeService service;
    private AppointmentStore appointmentStore;

    public CalendarChecker(final Listener listener, String domain, String username, String password, String ewsEndpoint)
    {
        this.listener = listener;
        appointmentStore = new AppointmentStore(new AppointmentStore.Listener()
        {
            @Override
            public void newAppointmentsAvailable(List<Appointment> appointments)
            {
                CalendarChecker.this.listener.newCalendarEventCreated(appointments);
            }
        });

        service = new ExchangeService(ExchangeVersion.Exchange2007_SP1);
        service.setCredentials(new WebCredentials(username, password, domain));
        service.setUrl(URI.create(ewsEndpoint));

    }

    public void startChecking()
    {
        final ItemRetriever itemRetriever = new ItemRetriever(service, new ItemRetriever.Listener()
        {
            @Override
            public void itemsRetrieved(List<Item> items)
            {
                List<Appointment> appointments = new ArrayList<Appointment>();

                for (Item item : items)
                {
                    appointments.add((Appointment) item);
                }

                try
                {
                    appointmentStore.storeAppointments(appointments);
                }
                catch (ServiceLocalException e)
                {
                    e.printStackTrace();
                }
            }
        });

        threadService.execute(itemRetriever);

        threadService.execute(new PullNotificationChecker(service, new PullNotificationChecker.Listener()
        {
            @Override
            public void itemsCreated()
            {
                concurrentService.execute(itemRetriever);
            }
        }));
    }

}
