package com.aksndr;

import com.lowagie.text.Utilities;
import com.lowagie.text.pdf.Barcode128;
import org.apache.commons.io.IOUtils;
import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.jaxb.Context;
import org.docx4j.model.structure.PageDimensions;
import org.docx4j.model.structure.PageSizePaper;
import org.docx4j.model.table.TblFactory;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.xml.bind.JAXBElement;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Aksndr on 30.10.2016.
 */
public class BCPrint {
    private WordprocessingMLPackage wordMLPackage;
    private MainDocumentPart wordDocumentPart;
    private ObjectFactory factory;

    Logger logger = LoggerFactory.getLogger(BCPrint.class);

    public BCPrint() {
    }

    private static void setCellWidth(Tc tableCell, int width) {

        TcPr tcPr = new TcPr();

        TblWidth cellWidth = new TblWidth();
        cellWidth.setW(BigInteger.valueOf(width));

        tcPr.setTcW(cellWidth);

        tableCell.setTcPr(tcPr);
    }

    public Map<String, Object> getSheet(List<String> barcodes) {

        try {
            wordMLPackage = new WordprocessingMLPackage();
            wordDocumentPart = new MainDocumentPart();
            factory = Context.getWmlObjectFactory();

            org.docx4j.wml.Body body = factory.createBody();
            setPageMargins(body);
            org.docx4j.wml.Document wmlDocumentEl = factory.createDocument();
            wmlDocumentEl.setBody(body);
            wordDocumentPart.setJaxbElement(wmlDocumentEl);
            wordMLPackage.addTargetPart(wordDocumentPart);


            Tbl table = TblFactory.createTable(barcodes.size() / 3, 3, 3900);

            List rows = table.getContent();

            int cellNum = 0;
            for (Object row1 : rows) {
                Tr row = (Tr) row1;
                setRowHeight(row, 1161);
                List cells = row.getContent();
                for (Object cell : cells) {
                    Tc tc = (Tc) cell;
                    addCellValue(tc, barcodes.get(cellNum));
                    cellNum++;
                }
            }

            wordDocumentPart.getContent().add(0, table);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wordMLPackage.save(bos);
            byte[] b = bos.toByteArray();
            try {
                bos.close();
            } catch (IOException e) {
                logger.error(e.toString());
                e.printStackTrace();
            }

            return succeed(b);
        } catch (Exception e) {
            return failed(e.toString());
        }
    }

    private void setPageMargins(org.docx4j.wml.Body body) {
        PageDimensions page = new PageDimensions();
        page.setPgSize(PageSizePaper.A4, false);

        SectPr.PgMar pgMar = page.getPgMar();
        pgMar.setTop(BigInteger.valueOf(396));
        pgMar.setBottom(BigInteger.valueOf(-10));
        pgMar.setLeft(BigInteger.valueOf(226));
        pgMar.setRight(BigInteger.valueOf(280));

        SectPr sectPr = factory.createSectPr();
        sectPr.setPgMar(pgMar);
        sectPr.setPgSz(page.getPgSz());
        body.setSectPr(sectPr);
    }

    private void addCellValue(Tc tc, String content) throws Exception {

        //P p = wordDocumentPart.createParagraphOfText(content);
        org.docx4j.wml.P p = factory.createP();
        org.docx4j.wml.R r = factory.createR();

        r.getContent().add(getBarcodeDrawning(content));
        p.getContent().add(r);

        org.docx4j.wml.R r2 = factory.createR();
        org.docx4j.wml.Text t = factory.createText();
        t.setValue(content);
        r2.getContent().add(t);
        p.getContent().add(r2);

        PPr pPr = factory.createPPr();
        Jc justification = factory.createJc();
        justification.setVal(JcEnumeration.CENTER);
        pPr.setJc(justification);

        p.setPPr(pPr);
        tc.getContent().set(0, p);
    }

    private Drawing getBarcodeDrawning(String s) throws Exception {

        byte[] bytes = getBarcodeBytes(s);
        org.docx4j.wml.Drawing drawing = factory.createDrawing();
        if (bytes!=null){
            BinaryPartAbstractImage imagePart = BinaryPartAbstractImage.createImagePart(wordMLPackage, bytes);
            Inline inline = imagePart.createImageInline(null, s, 0, 1, 3000, false);
            drawing.getAnchorOrInline().add(inline);
        }
        return drawing;
    }

    private byte[] getBarcodeBytes(String s) {
        Barcode128 code = new Barcode128();
        code.setCode(s);
        code.setBaseline(12);
        code.setSize(12);
        code.setBarHeight(Utilities.millimetersToPoints(10));
        code.setX(1.16f); //.16

        Image bCode = code.createAwtImage(java.awt.Color.BLACK, java.awt.Color.WHITE);

        byte[] imageBytes = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            BufferedImage bufferedImage = toBufferedImage(bCode);
            ImageIO.write(bufferedImage, "png", baos);
            baos.flush();
            imageBytes = baos.toByteArray();
        } catch (Exception e) {
            logger.error(e.toString());
        } finally {
            IOUtils.closeQuietly(baos);
        }
        return imageBytes;
    }

    private BufferedImage toBufferedImage(Image src) {
        int w = src.getWidth(null);
        int h = src.getHeight(null);
        int type = BufferedImage.TYPE_INT_RGB;
        BufferedImage d = new BufferedImage(w, h, type);
        Graphics2D g2 = d.createGraphics();
        g2.drawImage(src, 0, 0, null);
//        g2.dispose();
        return d;
    }

    private void addTableCell(Tr tableRow, String content) {
        Tc tableCell = factory.createTc();
        tableCell.getContent().add(wordDocumentPart.createParagraphOfText(content));
        tableRow.getContent().add(tableCell);
    }

    private void addTableCellWithWidth(Tr row, String content, int width) {
        Tc tableCell = factory.createTc();
        tableCell.getContent().add(
                wordMLPackage.getMainDocumentPart().createParagraphOfText(
                        content));

        if (width > 0) {
            setCellWidth(tableCell, width);
        }
        row.getContent().add(tableCell);
    }

    private void setRowHeight(Tr row, int height) {
        TrPr trPr = new TrPr();
        CTHeight ctHeight = new CTHeight();
        ctHeight.setHRule(STHeightRule.EXACT);
        ctHeight.setVal(BigInteger.valueOf(height));
        JAXBElement<CTHeight> jaxbElement = factory.createCTTrPrBaseTrHeight(ctHeight);
        trPr.getCnfStyleOrDivIdOrGridBefore().add(jaxbElement);
        row.setTrPr(trPr);
    }

    private void addBorders(Tbl table) {
        table.setTblPr(new TblPr());
        CTBorder border = new CTBorder();
        border.setColor("auto");
        border.setSz(new BigInteger("4"));
        border.setSpace(new BigInteger("0"));
        border.setVal(STBorder.SINGLE);

        TblBorders borders = new TblBorders();
        borders.setBottom(border);
        borders.setLeft(border);
        borders.setRight(border);
        borders.setTop(border);
        borders.setInsideH(border);
        borders.setInsideV(border);
        table.getTblPr().setTblBorders(borders);
    }

    private static Map<String, Object> succeed() {
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }

    private static Map<String, Object> succeed(Object value) {
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("value", value);
        return result;
    }

    private static Map<String, Object> failed(String errMsg) {
        Map<String, Object> result = new HashMap<>();
        result.put("ok", false);
        result.put("errMsg", errMsg);
        return result;
    }
}
