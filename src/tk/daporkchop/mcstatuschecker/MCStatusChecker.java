package tk.daporkchop.mcstatuschecker;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import javax.imageio.ImageIO;

import org.apache.commons.codec.binary.Base64;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

import ch.jamiete.mcping.MinecraftPing;
import ch.jamiete.mcping.MinecraftPingReply;
import fi.iki.elonen.NanoHTTPD;

public class MCStatusChecker extends NanoHTTPD {

	public static HashMap<String, ServerInfo> info = new HashMap<>();
	public static String otherServerLinks = "";
	
	public static long viewCount = 0;

	public MCStatusChecker() throws IOException {
		super(8080);
		start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
		System.out.println("\nRunning! Point your browsers to http://localhost:8080/ \n");
	}

	public static void main(String[] args) throws IOException {
		info.put("9b9t.com", new MCStatusChecker.ServerInfo());
		new Timer().scheduleAtFixedRate(new MCStatusChecker.UpdateStatus("9b9t.com"), 1, 10000); //10 mins
		info.put("2b2t.org", new MCStatusChecker.ServerInfo());
		new Timer().scheduleAtFixedRate(new MCStatusChecker.UpdateStatus("2b2t.org"), 1, 10000); //10 mins
		for (String s : info.keySet())	{
			otherServerLinks += "<a href=\"http://www.daporkchop.tk/servertracker/?serveraddress=" + s + "\" target=\"_top\">" + s + "</a><p></p>";
		}
		try {
			new MCStatusChecker();
		} catch (IOException ioe) {
			System.err.println("Couldn't start server:\n" + ioe);
		}
	}

	@Override
	public Response serve(IHTTPSession session) {
		switch (session.getUri()) {
		case "/":
		case "/index.html":
		case "/index.html/":
			String opt = session.getParms().getOrDefault("serveraddress", "2b2t.org");
			String msg = "";
			String serverName = opt.split("\\.")[0];
			if (info.get(opt).status != null) {
				msg = HTML.indexOnline;
				msg = msg.replace("INSSTATE", "green\">Online");
				msg = msg.replace("INSMOTD", info.get(opt).status.getDescription().getText());
				msg = msg.replace("INSVERSION", info.get(opt).status.getVersion().getName());
				msg = msg.replace("INSONLINE", info.get(opt).status.getPlayers().getOnline() + "");
				msg = msg.replace("INSMAX", info.get(opt).status.getPlayers().getMax() + "");
				msg = msg.replace("INSFAVICON", "<img src=\"" + info.get(opt).favicon + "\"></img>");
				msg = msg.replace("INSDIVSTYLE1", "30%;");
				msg = msg.replace("INSDIVSTYLE2", "30%;");
				msg = msg.replace("INSDIVSTYLE3", "30%;");
				msg = msg.replace("INSGRAPH", info.get(opt).graph);
				msg = msg.replaceAll("INSNAME", serverName);
				msg = msg.replace("INSSERVERS", otherServerLinks);
			} else {
				msg = HTML.indexOffline;
				msg = msg.replace("INSSTATE", "red\">Offline");
				msg = msg.replace("INSFAVICON", "<img src=\"" + info.get(opt).favicon + "\"></img>"); //TODO: favicon
				msg = msg.replace("INSDIVSTYLE1", "100%;");
				msg = msg.replace("INSGRAPH", info.get(opt).graph);
				msg = msg.replaceAll("INSNAME", serverName);
				msg = msg.replace("INSSERVERS", otherServerLinks);
			}
			if (++viewCount % 2 == 0)	{
				System.out.println("Pageview #" + (viewCount / 2));
			}
			return newFixedLengthResponse(msg);
		case "/ad.html":
			return newFixedLengthResponse(
					"<html><head><meta http-equiv=\"refresh\" content=\"1;url=http://www.daporkchop.tk/toembed/adrefresh.html\" /></head><body><p>AutoRefresh</p></body></html>");
		}
		return null;
	}

	public static class UpdateStatus extends TimerTask {
		private String url;
		public UpdateStatus (String url)	{
			this.url = url;
		}
		private int writeCount = 0;
		@Override
		public void run() {
			try {
				MCStatusChecker.info.get(url).status = new MinecraftPing().getPing(url);
				MCStatusChecker.info.get(url).favicon = MCStatusChecker.info.get(url).status.getFavicon();
				if (writeCount == 0)	{
					writeCount = 60;
					MCStatusChecker.info.get(url).onlinePlayers.add(MCStatusChecker.info.get(url).status.getPlayers().getOnline());
					if (MCStatusChecker.info.get(url).onlinePlayers.size() > 144)	{
						MCStatusChecker.info.get(url).onlinePlayers.remove(0);
					}
					MCStatusChecker.info.get(url).graph = genGraph();
					Files.write(Paths.get(url + ".txt"), (System.currentTimeMillis() + " " + MCStatusChecker.info.get(url).status.getPlayers().getOnline() + " " + MCStatusChecker.info.get(url).status.getPlayers().getMax() + "\n").getBytes(), StandardOpenOption.APPEND);
				} else {
					writeCount--;
				}
			} catch (IOException e) {
				MCStatusChecker.info.get(url).status = null;
				MCStatusChecker.info.get(url).favicon = Favicons.fallback;
			    try {
			    	if (writeCount == 0)	{
						writeCount = 60;
						MCStatusChecker.info.get(url).onlinePlayers.add(0);
						if (MCStatusChecker.info.get(url).onlinePlayers.size() > 144)	{
							MCStatusChecker.info.get(url).onlinePlayers.remove(0);
						}
						MCStatusChecker.info.get(url).graph = genGraph();
						Files.write(Paths.get(url + ".txt"), (System.currentTimeMillis() + " 0 0 OFFLINE\n").getBytes(), StandardOpenOption.APPEND);
					} else {
						writeCount--;
					}
			    } catch (IOException e1) {}
			}
		}
		public String genGraph()	{
			DefaultCategoryDataset data = new DefaultCategoryDataset();
			for (int i = 0; i < MCStatusChecker.info.get(url).onlinePlayers.size(); i++)	{
				int a = MCStatusChecker.info.get(url).onlinePlayers.get(i);
				data.addValue(a, "Players", Integer.toString(i));
			}
			JFreeChart lineChartObject = ChartFactory.createLineChart("Online Players (Last 24 Hours)", "Time", "Player Count", data, PlotOrientation.VERTICAL, false, true, false);
			int width = 640;
		    int height = 480;
		    ByteArrayOutputStream stream = new ByteArrayOutputStream();
		    try {
				ChartUtilities.writeChartAsPNG(stream, lineChartObject, width, height);
			} catch (IOException e) {}
		    return Base64.encodeBase64String(stream.toByteArray());
		}
	}
	public static class ServerInfo {
		public MinecraftPingReply status;
		public String favicon;
		public ArrayList<Integer> onlinePlayers = new ArrayList<>();
		public String graph;
		
		public ServerInfo()	{
			status = null;
			favicon = Favicons.fallback;
			graph = "data:image/png;base64,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6fzHxbZfbF3QV+vnNv/K/w996EN+uYgCKKWfVBjSv15/+Zd/Obs6OP2lmz6mcVoDa+EiuOuxAKakC13++I//OLtNVrpKL32f6dyitMTJPffck81+3uoCmJLuTJFmQ9KJ5OlWfO94xztuelV1ftKV0QsPEab1/QplJe/Lcu/wkGbq0n7S+V6plKZ/bKTTCtL5iGkGqJikNfrybwW3nK9rOeVoJe/3Sr639PWnfac7ruRf0byZC2BKOtqQ7vqRZtXSIfkkXbSS7q+bXiuU3B2A0qkO6XPT3YyKvQhkrX5u020Jc/tMV927FZwogCLyqmfhGmppgeeNknTLukKLM4vcqqSymvu5TBeOiCiAIvKq5syZM3PrvuV84AMf2DDfX7rlWe7WYWmmMp2nJXIr89nPfnbuMHY6XebChQveFFEAReTVyWOPPbboYcB0McNGS5r5y31/aa1FkVuZbdu2zf087tq1yxsiCqCI3JoCmM7/S2vm/emf/ml2JxcREVEARUREREQBFBEREREFUEREREQUQBERERFRAEVEREREARQRERERBVBEREREFEARERERUQBFRERERAEsnHe84x3zxunm22984xsBAErewh6jAK6wAKZbXn3sYx8DACh5CqACCAAogAqgAggAKIAKoAIIACiACqACCAAogAqgHygAQAFUAAEAFEAFEABAAVQAAQAUwGWnpaUlbrvttnnPpfFCVVVVBfex2PYKIACgAJZgAXz88cejqanppoXtySefjAcffHDJAmgGEABQAEu8AF69ejXq6urimWeeuWmBu+uuu+LSpUsKIADAei6AHR0d8eijj960wB0/fjzuv//+JfeVPr+mpiYqKyujoaEhenp6YmZmRgEEABTAUimAZ86cyQ79zs7O3rQAptm/0dHRZe13amoqhoaGorm5Obq6uhRAAEABLJUCmMrfhQsXbnoId2BgIPbt21f0/sfGxqK6urpg8cvJT2tra4yMjMTg4GD2xqaPxsbGxsbGxsalOF6XBXCxq3YXK4GpKJ4+fbro/Y+Pj0dtba0ZQADADGCprgO4WPl7+umnswK4nO3b29tjeHg4pqens8PFbW1t0dnZqQACAArgeiqAaX3A/v7+ZW3f19cXjY2NUVFREfX19dHd3R2Tk5MKIACgALoTiAIIACiACqACCAAogAogAIACqAACACiACiAAgAKo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+QWwmBw5ciR27doVFy9ezKTHR48eVQABAAWwVA8BX79+PSorK1dcAJuamrJZxfwZxubmZgUQAFAAS7EAvvTSS/HII4/E3r175xXAmpqarBQ2NDRET09PzMzMFNxHVVVVXL58eW48MTGRHQ5WAAEABbDECmDu/L4777wznn322Rten5qaiqGhoWw2r6urq+B+ysrKYnp6em6cHpeXlxcsfjn5aW1tjZGRkRgcHMze2PTR2NjY2NjY2LgUx+t+BjCdA/jQQw9lF4UUytjY2JIzemYAAQAzgOvsHMBUArdt21bw9fHx8aitrXUOIADAei2A9957b4yOjmaHatPs3gMPPDDvHMD29vYYHh7OXk/btbW1RWdnZ8GLRHp7e10FDAAogKVcAPv6+qKxsTG2bNkSdXV10dHREVevXr3h9bROYH19fXR3d8fk5GTBApi/DmBy8OBB6wACAAqgO4EogACAAqgAKoAAgAKoAAIAKIAKIACAAqgAAgAogAogAIACqAACACiACiAAgAKoAAIAKIAKIACAAqgAAgCs6wI4MDAQLS0t2b1+d+zYEffdd1+88MILc6+fOHEidu/eHVu3bs1e379/f1y5cqXg/tK9gRdSAAEABbCECmAqd0899VS8+OKLce3atXj44Yez53LZs2dPVhKvX78eExMT0dHREXv37l2yAJoBBAAUwHV0CDgVvcrKyoKvp5JYVVWlAAIAbIQC+NJLL8Ujjzyy5Axff39/dsh4qQJYU1OTlciGhobo6emJmZkZBRAAUABLrQDmzte7884749lnn110m3PnzsXOnTvj/PnzN93f1NRUDA0NRXNzc3R1dRUsfjn5aW1tjZGRkRgcHMze2PTR2NjY2NjY2LgUx+t+BjAd3n3ooYcWneE7efJkVv5OnTpV1D7HxsaiurraDCAAYAawVM8BTCVw27Zt8547duxYdgXw2bNni97f+Ph41NbWKoAAgAJYKgXw3nvvjdHR0Ziens5m6x544IF55wAePnw46urqsm2Wc9FHe3t7DA8PZ/tLn9PW1hadnZ0KIACgAJZKAezr64vGxsbYsmVLVvTSMi9Xr15dcl2/JF0tvFgBzO0vrStYX18f3d3dMTk5qQACAAqgO4EogACAAqgAKoAAgAKoAAIAKIAKIACAAqgAAgAogAogAIACqAACACiACiAAgAKoAAIAKIAKIACA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OYBLUQABABRABRAAYLMvA3PixInYvXt3bN26NXbs2BH79++PK1euLPv15ZRSBRAAUADXsAB++MMfjte//vVRXl4+91xzc3OcOnVqWZ+/Z8+eGBgYiOvXr8fExER0dHTE3r17l/36WixNowACAArgMnP8+PFFZ9oef/zxeOc737mifV67di2qqqpW/LoCCAAogK9gAUxF6d57781m5vKL1/PPP58drl1J+vv7o6WlZcWvp6+jpqYmW6OwoaEhenp6brpWoQIIACiAy0yaibt69eoNM29TU1Nx++23F72/tJzMzp074/z58yt6PT/paxgaGsoOR3d1dRUsfjn5aW1tjZGRkRgcHMze2PTR2NjY2NjY2LgUx696Abzjjjvi4sWLNxTAz372s9ksXDE5efJkVu4KnTt4s9cLZWxsLKqrq80AAgBmANeiAKarc9Ph2HQ3kFQA06Hg9IW87nWvi3vuuaeoBaXTIeOzZ8+u6PWlMj4+HrW1tQogAKAArkUBTFOP6erfhcuuVFRUZIdrl5PDhw9HXV1djI6Oruj1hRd9tLe3x/DwcHZnkvQ5bW1t0dnZqQACAArgWi0D86lPfSqbBUwXXSRvetOb4jOf+cyqF5NOy74s9/X89PX1RWNjY1ZC6+vro7u7OyYnJxVAAEABXIsCmIrW+9///rh06VKs5yiAAIACWOTsXVlZWbz5zW/OFoVO6/QpgAAAG7QAptu0vetd78ou0MiVwXQYOJ2H91//9V/xpS99SQEEANho5wCmzM7OZlfopvPt0uLLuTJ45513xsMPP1zyRVABBAAUwFXkueeei/vvv3/eBRvvec97FEAAgI1UANPdQHp7e7O7bqTzAVPpS2sB/uM//mO2HmCaCVQAAQA2QAFMS67s27cvtmzZMncxyF/8xV/Exz/+8eywcMrLL798w1ItCiAAwDq/Cnj79u3x4IMPzt0WbmHSFcIKIADABiiAf/Inf5It/XKzhZZdBQwAsEEKYFrz72//9m+zW7Xlzv3LpwACAGywArjwal8FEABggxfAdHXvhz70oZiZmckK3/T0dHzuc5+LPXv2xJEjR5a9mPTu3btj69at2YLS+/fvjytXrsxbY/DAgQNRXV2dnWt46NChuQtMCq1JWMz2CiAAoAAWkXTYd2pqau5xKoApExMT8drXvnZZ+0hlcWBgIK5fv559XkdHR+zd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3BxdXV0Fi19OflpbW2NkZCQGBwezNzZ9NDY2NjY2NjYuxfGGLoAnT57Myt+pU6eK+ryxsbGorq42AwgAmAFcTwXw2LFj2RXAZ8+eLfpzx8fHo7a2VgEEABTA9VIADx8+HHV1dTE6Orqsiz7a29tjeHg4pqens89pa2uLzs5OBRAAUADXSwFcbF2/JC0Ls1gB7Ovri8bGxqioqIj6+vro7u6OyclJBRAAUADdCUQBBAAUQAVQAQQAFEAFEABAAVQAAQAUQAUQAEABVAABABRABRAAQAFUAAEAFEAFEABAAVQAAQAUQAUQAGDDFsDZ2dk4cOBAVFdXx/bt2+PQoUPZc2u1vQIIACiAJZYjR47Erl274uLFi5n0+OjRo2u2vQIIACiAJZampqYYGBiYG6fHzc3Na7a9AggAKIAllqqqqrh8+fLceGJiIju8u1bbK4AAgAJYYikrK4vp6em5cXpcXl6+JtunNywnP21tbbFnz55obW2NN77xjdlHY2NjY2NjY+NSHJsBXOEMoIiIiMhmiXMARURERBTA0ktvb++SV/XedtttRW0vIiIiogCWePLX9UsOHjw4b12/hQXwZtsvJ/nnBgIAlLoNVwBFRDZKiv1LWkRk084AiogogCIiCqCIiIiIKIAiIiIiogCKiIiIiAIoIiIiogCKiIiIiAIoIiIiIgqgiIisMPmL02/fvj0OHTpU9OL0IiIKoIjIOsqRI0fcnlJEFEARkc2UpqamGBgYmBunx83Nzd4YEVEARUQ2aqqqquLy5ctz44mJiexwsIiIAigiskFTVlYW09PTc+P0uLy83BsjIgqgiMhGjRlAEVEARUQ2WZwDKCIKoIjIJktvb6+rgEVEARQR2UzJXwcwOXjwoHUARUQBFBEREREFUEREREQUQBERERFRAEVEREREARQRERERBVBEREREFEARERERBVBEREREFEARERERUQBFRERERAEUEREREQVQRERERBRAEZHNkdtuuy0jIqIAiohskGJXiAIoIgqgiMgmKIMiIgqgiIgCWHBG8LHHHovXve51sWXLlmhsbIyBgYF43/veFzt27IjKysq4++67Y3R0dN6+Pv3pT0dbW1tUVVVlmpqass8TEVEARUTWQQF829veFleuXIm+vr655+655564evVqfPSjH83GqeDlcvLkybj99tujpaUlvvjFL2bb3Xvvvdl2TzzxhP8IIqIAioiUegG8cOFCNn755ZdveO5LX/pSlJWVZbODuaTil7b5/Oc/P/fcxMRE9tzrX/96/xFERAEUESn1AphK3sLnpqenC35eOixc6IKT8vJy/xFERAEUESn1Anizzy9UANOsn4iIAigisgkK4Jve9KZs/LGPfcwbLiIKoIjIZiiAp0+fzs4JTFcOnzlzJjtcPDY2Fo8//ng0Nzf7jyAiCqCIyEYrgClDQ0PZ1cOvec1rsiuC05Ix73znO+PUqVP+I4iIAigiIiIiCqCIiIiIKIAiIiIiogCKiIiIKIAiIiIiogCKiIiIiAIoIiIiIgqgiIiIiKyT/H8bLbGRjj1KfgAAAABJRU5ErkJggg==";
		}
	}
}
