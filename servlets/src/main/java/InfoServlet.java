import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * SaxonServlet. Transforms a supplied input document using a supplied stylesheet
 */

@SuppressWarnings("serial")
public class InfoServlet extends HttpServlet {

	ServletOutputStream out;
	String xslPathFileName = "/uicds/xsl/info.xsl";
	String manifestPathFileName = "/META-INF/MANIFEST.MF";

	public void init() throws ServletException {
		super.init();
	}

	/**
	 * service() - accept request and produce response<BR>
	 * @param req The HTTP request
	 * @param res The HTTP response
	 */

	public void service(HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException
	{
		out = res.getOutputStream();
		apply(req,res);
	}

	private void apply(HttpServletRequest req,HttpServletResponse res) {

		ServletContext application = getServletConfig().getServletContext();
		InputStream inputStream = application.getResourceAsStream(manifestPathFileName);
		Manifest manifest = null;
		try {
			manifest = new Manifest(inputStream);
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
		}

		Attributes manifestAttributes = manifest.getMainAttributes();

		Document xmlDoc = makeDoc(manifestAttributes);

		try {
			URL styleURL = new URL(req.getScheme(), req.getServerName(), req.getServerPort(), xslPathFileName);
			Source source = new DOMSource(xmlDoc);
			TransformerFactory tFactory = TransformerFactory.newInstance();
			Transformer transformer = tFactory.newTransformer(new StreamSource(styleURL.openStream()));
			transformer.transform(source,new StreamResult(out));
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
		}
	}

	private Document makeDoc(Attributes manifestAttributes) {

		DocumentBuilder docBuilder = null;
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		try {
			docBuilder = docFactory.newDocumentBuilder();
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
		}
		Document xmlDoc = docBuilder.newDocument();

		Element rootElement = xmlDoc.createElement("xmlroot");

		Element entryElement = null;
		Element nameElement = null;
		Element valueElement = null;

		for(Object key : manifestAttributes.keySet()) {

			Object value = manifestAttributes.get(key);

			entryElement = xmlDoc.createElement("entry");
			rootElement.appendChild(entryElement);

			nameElement = xmlDoc.createElement("name");
			nameElement.appendChild(xmlDoc.createTextNode(key.toString()));
			entryElement.appendChild(nameElement);

			valueElement = xmlDoc.createElement("value");
			valueElement.appendChild(xmlDoc.createTextNode(value.toString()));
			entryElement.appendChild(valueElement);
		}

		xmlDoc.appendChild(rootElement);
		return xmlDoc;	
	}

	/**
	 * getServletInfo<BR>
	 * Required by Servlet interface
	 */

	public String getServletInfo() {
		return "Apply an XSLT stylesheet to a source XML document";
	}
}
