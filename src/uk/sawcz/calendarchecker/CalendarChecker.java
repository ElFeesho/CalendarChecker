package uk.sawcz.calendarchecker;

import microsoft.exchange.webservices.data.*;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


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
        private final FileWatcher acceptedWatcher;
        private final FileWatcher declineWatcher;
        private final FileWatcher tentativeWatcher;

        public interface Listener
        {
            void itemsCreated();
        }

        private final ExchangeService service;
        private final PullNotificationChecker.Listener listener;

        public PullNotificationChecker(final ExchangeService service, PullNotificationChecker.Listener listener)
        {
            this.service = service;
            this.listener = listener;

            acceptedWatcher = new FileWatcher("outbox/accept/", new FileWatcher.Listener()
            {
                @Override
                public void fileCreated(File file)
                {
                    acceptAppointment(file.getName());
                    file.delete();
                }
            });

            declineWatcher = new FileWatcher("outbox/decline/", new FileWatcher.Listener()
            {
                @Override
                public void fileCreated(File file)
                {
                    declineAppointment(file.getName());
                }
            });

            tentativeWatcher = new FileWatcher("outbox/tentative/", new FileWatcher.Listener()
            {
                @Override
                public void fileCreated(File file)
                {
                    tentativeAppointment(file.getName());
                }
            });
        }

        private void tentativeAppointment(String id)
        {
            System.out.println("TENTATIVE: "+id);
        }

        private void declineAppointment(String id)
        {
            System.out.println("DECLINING "+id);

        }

        private void acceptAppointment(String id)
        {
            System.out.println("ACCEPTING "+id);
            try
            {
                System.out.println("Finding "+id);

                Appointment appointment = Appointment.bind(service, ItemId.getItemIdFromString(id.replace("_","/")));
                if(appointment != null)
                {
                    System.out.println("FOUND IT!");
                    appointment.accept(true);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
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
                    acceptedWatcher.poll();
                    declineWatcher.poll();
                    tentativeWatcher.poll();
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
        private Date lastCheckDate = new Date(System.currentTimeMillis()- TimeUnit.DAYS.toMillis(10));

        public interface Listener
        {
            public void itemsRetrieved(List<Appointment> items);
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
                CalendarView calendarView = new CalendarView(lastCheckDate, new Date(System.currentTimeMillis()+TimeUnit.DAYS.toMillis(10)), 50);
                lastCheckDate = new Date();

                FindItemsResults<Appointment> appointments = service.findAppointments(WellKnownFolderName.Calendar, calendarView);

                System.out.println("Found items: " + appointments.getItems().size());


                listener.itemsRetrieved(appointments.getItems());
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
            public void itemsRetrieved(List<Appointment> items)
            {
                try
                {
                    appointmentStore.storeAppointments(items);
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
