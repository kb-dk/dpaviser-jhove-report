#!/usr/bin/env perl -l
use XML::XPath;
use XML::XPath::XMLParser;

# Initial prototype of report needed by Soeren Bombaek.  If full Excel is needed
# rewrite in Java.

# pipe "find somewhere -print" into this script, to give input on the form:

# .../JYP20150807V15#0011.pdf.md5
# .../e527465e.xml
# .../JYP20150807L11#0016.pdf
# .../JYP20150807L11#0020.pdf.md5

my %seen;                       # key on form "NOV20150807?13" (question mark to sort properly)

while (<>) {
    chomp;                      # get rid of \n

    if (m!/([A-Z]{3}[^/]+)[A-Z](\d.)#\d\d\d\d\.pdf$!) { # infomedia PDF?

        $seen{"$1?$2"}++; 

    } elsif (m!\.xml$!) {       # xml?
        
                                # http://mkweb.bcgsc.ca/intranet/perlbook/pxml/ch08_02.htm
        my $xpath = XML::XPath->new(filename => $_); 
        
        my $section = $xpath->find('string(//Property[@FormalName="PSNA"]/@Value)')->to_literal();

        my $pdf_file = $xpath->find('//media[@media-type="PDF"]/media-reference[@exists=1][1]/text()')->to_literal();
        $pdf_file =~ s/^\s+|\s+$//g; # trim whitespace

        # <media-reference exists="1">JYP\2015\08\07\JYP20150807V15#0007.pdf</media-reference>
        if ($pdf_file =~ m![/\\]([A-Z]{3}[^/]+)[A-Z](\d.)#\d\d\d\d\.pdf$!) {
            $key = "$1?$2";
            $section_for{$key} = $section;
        } else {
            # some XML files do not have PDF link.
        }
    } else {
        # Other files are ignored.
    }
}

# JYP20150807?13        24 sider        Erhverv
# JYP20150807?14        16 sider        Biler
# JYP20150807?15        12 sider        JP Aarhus

foreach my $key (sort keys %seen) {
    print "$key\t$seen{$key} sider\t$section_for{$key}";
}

# (tell emacs to not use tab characters)
# Local Variables:
# indent-tabs-mode: nil
# End:
