<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:j="http://hul.harvard.edu/ois/xml/ns/jhove"
                xmlns:mix="http://www.loc.gov/mix/v20"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://hul.harvard.edu/ois/xml/ns/jhove http://hul.harvard.edu/ois/xml/xsd/jhove/1.3/jhove.xsd
http://www.loc.gov/mix/v20 http://www.loc.gov/standards/mix/mix20/mix20.xsd"
                exclude-result-prefixes="j mix xsi">

    <xsl:output omit-xml-declaration="yes" indent="yes"/>

    <xsl:template match="/">
        <xsl:text disable-output-escaping='yes'>&lt;!DOCTYPE html&gt;</xsl:text>

        <html>
            <table>
                <xsl:apply-templates/>
            </table>
        </html>
    </xsl:template>

    <xsl:template match="/j:jhove/j:repInfo">
        <tr>
            <td><xsl:value-of select="@uri"/></td>
            <td>PDF</td>

            <!-- General properties-->
            <td><xsl:value-of select="./j:size/text()"/></td>
            <td><xsl:value-of select="./j:format/text()"/></td>
            <td><xsl:value-of select="./j:version/text()"/></td>
            <td><xsl:value-of select="./j:status/text()"/></td>
            <td><xsl:for-each select="./j:profiles/j:profile">
              <xsl:if test="not(position() = 1)">, </xsl:if>
              <xsl:value-of select="."/>
            </xsl:for-each></td>

            <!-- Metadata -->
            <xsl:variable name="INFO" select="j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text()='Info']/j:values"/>
            <td><xsl:value-of select="$INFO/j:property[j:name='Title']/j:values/j:value/text()"/></td>
            <td><xsl:value-of select="$INFO/j:property[j:name='Producer']/j:values/j:value/text()"/></td>
            <td><xsl:value-of select="$INFO/j:property[j:name='CreationDate']/j:values/j:value/text()"/></td>
            <td><xsl:value-of select="$INFO/j:property[j:name='ModDate']/j:values/j:value/text()"/></td>

            <!-- Images -->
            <xsl:variable name="IMAGES" select="j:properties/j:property/j:values/j:property[j:name='Images']"/>
            <xsl:variable name="JPEG" select="count($IMAGES/j:values/j:property[j:name='Image']/j:values/j:property[j:name='NisoImageMetadata']/j:values/j:value/mix:mix/mix:BasicDigitalObjectInformation/mix:Compression/mix:compressionScheme[text()='JPEG'])"/>
            <td>
                <xsl:value-of select="$JPEG"/>
            </td>
            <xsl:variable name="UNCOMPRESSED" select="count($IMAGES/j:values/j:property[j:name='Image']/j:values/j:property[j:name='NisoImageMetadata']/j:values/j:value/mix:mix/mix:BasicDigitalObjectInformation/mix:Compression/mix:compressionScheme[text()='Uncompressed'])"/>
            <td>
                <xsl:value-of select="$UNCOMPRESSED"/>
            </td>
            <xsl:variable name="FLATEDECODE" select="count($IMAGES/j:values/j:property[j:name='Image']/j:values/j:property[j:name='Filter']/j:values/j:value[text()='FlateDecode'])"/>
            <td>
                <xsl:value-of select="$FLATEDECODE"/>
            </td>
            <td>
                <xsl:value-of select="count($IMAGES/j:values/j:property[j:name='Image']) - $JPEG - $UNCOMPRESSED - $FLATEDECODE"/>
            </td>

            <!-- Fonts -->
            <xsl:variable name="FONTS" select="j:properties/j:property/j:values/j:property[j:name='Fonts']"/>
            <xsl:variable name="TYPE0" select="count($FONTS/j:values/j:property[j:name='Type0']/j:values/j:property[j:name='Font'])"/>
            <td>
                <xsl:value-of select="$TYPE0"/>
            </td>
            <xsl:variable name="TYPE1" select="count($FONTS/j:values/j:property[j:name='Type1']/j:values/j:property[j:name='Font'])"/>
            <td>
                <xsl:value-of select="$TYPE1"/>
            </td>
            <xsl:variable name="TRUETYPE" select="count($FONTS/j:values/j:property[j:name='TrueType']/j:values/j:property[j:name='Font'])"/>
            <td>
                <xsl:value-of select="$TRUETYPE"/>
            </td>
            <xsl:variable name="ALLFONTS" select="count($FONTS/j:values/j:property/j:values/j:property[j:name='Font'])"/>
            <td>
                <xsl:value-of select="$ALLFONTS - $TYPE0 - $TYPE1 - $TRUETYPE"/>
            </td>
            <xsl:variable name="UNEMBEDDEDFONTSTEMP" select="$FONTS/j:values/j:property/j:values/j:property[j:name/text()='Font'][j:values/j:property[j:name/text()='FontDescriptor'] and not(j:values/j:property[j:name/text()='FontDescriptor']/j:values/j:property/j:name[starts-with(text(),'FontFile')])]/j:values/j:property[j:name/text()='FontDescriptor']/j:values/j:property[j:name/text()='FontName']/j:values/j:value
"/>
            <xsl:variable name="UNEMBEDDEDFONTS">
                <xsl:for-each select="$UNEMBEDDEDFONTSTEMP">
                  <xsl:if test="not(position() = 1)">, </xsl:if>
                  <xsl:value-of select="."/>
                </xsl:for-each>
            </xsl:variable>

            <td>
                <xsl:value-of select="$UNEMBEDDEDFONTS" />
            </td>

            <!-- Pages -->
            <td>
                <xsl:value-of select="count(j:properties/j:property/j:values/j:property[j:name='Pages']/j:values/j:property[j:name='Page'])"/>
            </td>
        </tr>
    </xsl:template>

    <xsl:template match="text()"/>

</xsl:stylesheet>