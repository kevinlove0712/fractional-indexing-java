// SPDX-License-Identifier: CC0-1.0
import dev.fractionalindexing.FractionalIndexing;
import dev.fractionalindexing.KeyLengthExceededException;
import java.io.*;
import java.util.*;

public class DifferentialRunner {
    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "US-ASCII"));
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(System.out, "US-ASCII"));
        String line;
        while ((line = in.readLine()) != null) {
            String[] p = line.split("\t", -1);
            String left = "~".equals(p[2]) ? null : p[2];
            String right = "~".equals(p[3]) ? null : p[3];
            try {
                List<String> keys;
                int count = Integer.parseInt(p[4]);
                int length = Integer.parseInt(p[5]);
                int batch = Integer.parseInt(p[6]);
                if (p[1].equals("S")) keys = Collections.singletonList(FractionalIndexing.generateKeyBetween(left, right, length));
                else if (p[1].equals("s")) keys = Collections.singletonList(FractionalIndexing.generateKeyBetween(left, right));
                else if (p[1].equals("B")) keys = FractionalIndexing.generateNKeysBetween(left, right, count, length, batch);
                else keys = FractionalIndexing.generateNKeysBetween(left, right, count);
                out.write(p[0] + "\tO\t");
                for (int i = 0; i < keys.size(); i++) {
                    if (i > 0) out.write(",");
                    out.write(keys.get(i));
                }
            } catch (KeyLengthExceededException e) {
                out.write(p[0] + "\tL");
            } catch (IllegalArgumentException e) {
                out.write(p[0] + "\tE");
            } catch (Throwable t) {
                out.write(p[0] + "\tUNEXPECTED\t" + t.getClass().getName() + ":" + t.getMessage());
            }
            out.newLine();
            out.flush();
        }
    }
}
