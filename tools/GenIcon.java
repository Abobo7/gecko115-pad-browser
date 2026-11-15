import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class GenIcon {
    public static void main(String[] args) throws Exception {
        String resDir = args[0];
        int[] sizes = {48, 72, 96, 144, 192};
        String[] dirs = {"drawable-mdpi", "drawable-hdpi", "drawable-xhdpi", "drawable-xxhdpi", "drawable-xxxhdpi"};

        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            float pad = s * 0.06f;
            float box = s - pad * 2;
            float rad = s * 0.24f;

            // Deep ocean-indigo to vibrant orange-violet gradient rounded icon
            GradientPaint bg = new GradientPaint(
                    pad, pad, new Color(0x1E, 0x3A, 0x8A),
                    s - pad, s - pad, new Color(0x7C, 0x3A, 0xED));
            g.setPaint(bg);
            g.fill(new RoundRectangle2D.Float(pad, pad, box, box, rad, rad));

            // Inner glowing globe circle
            float cx = s * 0.5f;
            float cy = s * 0.5f;
            float gr = s * 0.28f;
            g.setColor(new Color(255, 255, 255, 235));
            g.setStroke(new BasicStroke(Math.max(1.8f, s * 0.045f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Ellipse2D.Float(cx - gr, cy - gr, gr * 2, gr * 2));
            g.draw(new Ellipse2D.Float(cx - gr * 0.48f, cy - gr, gr * 0.96f, gr * 2));
            g.draw(new Line2D.Float(cx - gr * 0.95f, cy, cx + gr * 0.95f, cy));

            // Dynamic flame / comet arc (Firefox Gecko tribute)
            GradientPaint arcGrad = new GradientPaint(
                    cx - gr, cy + gr, new Color(0xF9, 0x73, 0x16),
                    cx + gr, cy - gr, new Color(0xFB, 0xBF, 0x24));
            g.setPaint(arcGrad);
            g.setStroke(new BasicStroke(Math.max(2.4f, s * 0.07f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float ar = s * 0.34f;
            g.draw(new Arc2D.Float(cx - ar, cy - ar, ar * 2, ar * 2, -45, 215, Arc2D.OPEN));

            g.dispose();
            File outDir = new File(resDir, dirs[i]);
            outDir.mkdirs();
            ImageIO.write(img, "PNG", new File(outDir, "ic_launcher.png"));
        }
    }
}
