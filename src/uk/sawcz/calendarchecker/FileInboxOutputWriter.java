package uk.sawcz.calendarchecker;

import microsoft.exchange.webservices.data.Appointment;
import microsoft.exchange.webservices.data.ServiceLocalException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
* Created by sawczc01 on 05/02/2015.
*/
public class FileInboxOutputWriter
{
    public void writeInboxFiles(List<Appointment> appointments) throws ServiceLocalException, IOException
    {
        for (Appointment appointment : appointments)
        {
            File appointmentFile = new File("inbox/" + appointment.getId().toString().replace("/", "_"));
            JSONObject appointmentObject = new JSONObject();
            appointmentObject.put("id", appointment.getId());
            appointmentObject.put("when", appointment.getStart());
            appointmentObject.put("subject", appointment.getSubject());
            appointmentObject.put("location", appointment.getLocation());


            FileOutputStream fout = new FileOutputStream(appointmentFile);
            fout.write(appointmentObject.toString().getBytes());
            fout.close();
        }
    }
}
