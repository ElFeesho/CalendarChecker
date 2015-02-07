package uk.sawcz.calendarchecker;

import microsoft.exchange.webservices.data.Appointment;
import microsoft.exchange.webservices.data.ServiceLocalException;

import java.util.ArrayList;
import java.util.List;

/**
* Created by sawczc01 on 06/02/2015.
*/
public class AppointmentStore
{
    public interface Listener
    {
        public void newAppointmentsAvailable(List<Appointment> appointments);
    }

    private final Listener listener;

    private List<Appointment> knownAppointments = new ArrayList<Appointment>();

    public AppointmentStore(Listener listener)
    {
        this.listener = listener;
    }

    public void storeAppointments(List<Appointment> appointments) throws ServiceLocalException
    {
        List<Appointment> foundAppointments = new ArrayList<Appointment>();
        for (Appointment potentialNewAppointment : appointments)
        {
            for (Appointment knownAppointment : knownAppointments)
            {
                if (knownAppointment.getId().equals(potentialNewAppointment.getId()))
                {
                    foundAppointments.add(potentialNewAppointment);
                }
            }
        }

        List<Appointment> newAppointments = new ArrayList<Appointment>();
        if (foundAppointments.size() != appointments.size())
        {
            for (Appointment appointment : appointments)
            {
                boolean found = false;
                for (Appointment foundAppointment : foundAppointments)
                {
                    if (foundAppointment.getId().equals(appointment.getId()))
                    {
                        found = true;
                        break;
                    }
                }
                if (!found)
                {
                    newAppointments.add(appointment);
                }
            }
        }
        if (newAppointments.size() > 0)
        {
            listener.newAppointmentsAvailable(newAppointments);
            knownAppointments.addAll(newAppointments);
        }

    }
}
