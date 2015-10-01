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
            <td>
                <xsl:value-of select="@uri"/>
            </td>
            <xsl:variable name="JPEG"
                          select="count(j:properties/j:property/j:values/j:property[j:name='Images']/j:values/j:property[j:name='Image']/j:values/j:property[j:name='NisoImageMetadata']/j:values/j:value/mix:mix/mix:BasicDigitalObjectInformation/mix:Compression/mix:compressionScheme[text()='JPEG'])"/>
            <td>
                <xsl:value-of select="$JPEG"/>
            </td>
            <xsl:variable name="UNCOMPRESSED"
                          select="count(j:properties/j:property/j:values/j:property[j:name='Images']/j:values/j:property[j:name='Image']/j:values/j:property[j:name='NisoImageMetadata']/j:values/j:value/mix:mix/mix:BasicDigitalObjectInformation/mix:Compression/mix:compressionScheme[text()='Uncompressed'])"/>
            <td>
                <xsl:value-of select="$UNCOMPRESSED"/>
            </td>
            <xsl:variable name="FLATEDECODE"
                          select="count(j:properties/j:property/j:values/j:property[j:name='Images']/j:values/j:property[j:name='Image']/j:values/j:property[j:name='Filter']/j:values/j:value[text()='FlateDecode'])"/>
            <td>
                <xsl:value-of select="$FLATEDECODE"/>
            </td>
            <td>-1</td>
            <!-- how to? -->
            <xsl:variable name="TYPE0"
                          select="count(j:properties/j:property/j:values/j:property[j:name='Fonts']/j:values/j:property[j:name='Type0']/j:values/j:property[j:name='Font'])"/>
            <td>
                <xsl:value-of select="$TYPE0"/>
            </td>
            <xsl:variable name="TYPE1"
                          select="count(j:properties/j:property/j:values/j:property[j:name='Fonts']/j:values/j:property[j:name='Type1']/j:values/j:property[j:name='Font'])"/>
            <td>
                <xsl:value-of select="$TYPE1"/>
            </td>
            <xsl:variable name="TRUETYPE"
                          select="count(j:properties/j:property/j:values/j:property[j:name='Fonts']/j:values/j:property[j:name='TrueType']/j:values/j:property[j:name='Font'])"/>
            <td>
                <xsl:value-of select="$TRUETYPE"/>
            </td>
            <xsl:variable name="ALLFONTS"
                          select="count(j:properties/j:property/j:values/j:property[j:name='Fonts']/j:values/j:property/j:values/j:property[j:name='Font'])"/>
            <td>
                <xsl:value-of select="$ALLFONTS - $TYPE0 - $TYPE1 - $TRUETYPE"/>
            </td>
            <td>
                <xsl:value-of
                        select="count(j:properties/j:property/j:values/j:property[j:name='Pages']/j:values/j:property[j:name='Page'])"/>
            </td>
        </tr>
    </xsl:template>

    <xsl:template match="text()"/>

</xsl:stylesheet>