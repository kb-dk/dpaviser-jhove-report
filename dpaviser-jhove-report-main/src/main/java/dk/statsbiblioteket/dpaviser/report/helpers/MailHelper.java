package dk.statsbiblioteket.dpaviser.report.helpers;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.util.List;

public class MailHelper {
    /**
        Use JavaMail to send the actual mail message to the provided addresses by default through localhost port 25.
        Defaults are used for everything else,
        so the usual JavaMail properties apply and can be provided as system properties.  Notably "mail.from" is relevant if
        the default sender is incorrect.
         */
    public static void sendMail(String filePathName, InternetAddress[] addresses, List<List<String>> md5cellRows, String today, byte[] report1bytes, byte[] report2bytes, byte[] report3bytes) throws MessagingException {
        Session session = Session.getDefaultInstance(System.getProperties());

        Multipart multipart = new MimeMultipart();

        Message message = new MimeMessage(session);
        // use "mail.from" system property if default is not usable!
        message.addRecipients(Message.RecipientType.TO, addresses);
        message.setSubject("Infomedia upload reports for " + filePathName);
        {
            BodyPart messageBodyPart = new MimeBodyPart();
            String bodyText = "\n";
            for (List<String> row : md5cellRows) {
                bodyText = bodyText + String.join(" ", row) + "\n";
            }
            messageBodyPart.setText(bodyText);
            multipart.addBodyPart(messageBodyPart);
        }
        {
            BodyPart messageBodyPart = new MimeBodyPart();
            DataSource source = new ByteArrayDataSource(report1bytes, "application/vnd.ms-excel");
            messageBodyPart.setDataHandler(new DataHandler(source));
            messageBodyPart.setFileName("sektioner-" + today + ".xls");
            multipart.addBodyPart(messageBodyPart);
        }
        {
            BodyPart messageBodyPart = new MimeBodyPart();
            DataSource source = new ByteArrayDataSource(report2bytes, "application/vnd.ms-excel");
            messageBodyPart.setDataHandler(new DataHandler(source));
            messageBodyPart.setFileName("pdf-" + today + ".xls");
            multipart.addBodyPart(messageBodyPart);
        }
        {
            BodyPart messageBodyPart = new MimeBodyPart();
            DataSource source = new ByteArrayDataSource(report3bytes, "application/vnd.ms-excel");
            messageBodyPart.setDataHandler(new DataHandler(source));
            messageBodyPart.setFileName("xml-" + today + ".xls");
            multipart.addBodyPart(messageBodyPart);
        }
        message.setContent(multipart);
        Transport.send(message);
    }
}
