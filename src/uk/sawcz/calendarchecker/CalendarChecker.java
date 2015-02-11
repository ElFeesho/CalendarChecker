package uk.sawcz.calendarchecker;

import microsoft.exchange.webservices.data.*;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class CalendarChecker
{
    public interface Listener
    {
        public void newCalendarEventCreated(List<Appointment> appointment);
    }

    private final ExecutorService operationQueue = Executors.newSingleThreadExecutor();
    private final ExecutorService concurrentService = Executors.newFixedThreadPool(3);

    private final FileWatcher acceptedWatcher;
    private final FileWatcher declineWatcher;
    private final FileWatcher tentativeWatcher;

    private final Listener listener;

    private ExchangeService service;

    public CalendarChecker(final Listener listener, String domain, String username, String password, String ewsEndpoint)
    {
        this.listener = listener;

        service = new ExchangeService(ExchangeVersion.Exchange2007_SP1);
        service.setCredentials(new WebCredentials(username, password, domain));
        service.setUrl(URI.create(ewsEndpoint));
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
                file.delete();
            }
        });

        tentativeWatcher = new FileWatcher("outbox/tentative/", new FileWatcher.Listener()
        {
            @Override
            public void fileCreated(File file)
            {
                tentativeAppointment(file.getName());
                file.delete();
            }
        });

    }

    private void tentativeAppointment(String id)
    {
        System.out.println("TENTATIVE: " + id);
        try
        {
            System.out.println("Finding " + id);

            Appointment appointment = Appointment.bind(service, ItemId.getItemIdFromString(id.replace("_", "/")));
            if (appointment != null)
            {
                System.out.println("FOUND IT!");
                appointment.acceptTentatively(true);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void declineAppointment(String id)
    {
        System.out.println("DECLINING " + id);
        try
        {
            System.out.println("Finding " + id);

            Appointment appointment = Appointment.bind(service, ItemId.getItemIdFromString(id.replace("_", "/")));
            if (appointment != null)
            {
                System.out.println("FOUND IT!");
                appointment.decline(true);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    private void acceptAppointment(String id)
    {
        System.out.println("ACCEPTING " + id);
        try
        {
            System.out.println("Finding " + id);

            Appointment appointment = Appointment.bind(service, ItemId.getItemIdFromString(id.replace("_", "/")));
            if (appointment != null)
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

    public void startChecking() throws InterruptedException
    {
        PullNotificationChecker notificationChecker = new PullNotificationChecker(service, new PullNotificationChecker.Listener()
        {
            @Override
            public void itemsCreated(List<Appointment> appointments)
            {
                listener.newCalendarEventCreated(appointments);
            }
        });

        while (true)
        {
            operationQueue.execute(acceptedWatcher);
            operationQueue.execute(tentativeWatcher);
            operationQueue.execute(declineWatcher);
            operationQueue.execute(notificationChecker);
            Thread.sleep(60000);
        }
    }

}
