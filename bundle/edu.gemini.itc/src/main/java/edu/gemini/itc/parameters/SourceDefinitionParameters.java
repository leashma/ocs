package edu.gemini.itc.parameters;

import edu.gemini.itc.shared.*;
import edu.gemini.itc.web.ITCRequest;


/**
 * This class holds the information from the Source Definition section
 * of an ITC web page.  This object is constructed from a servlet request.
 */
public final class SourceDefinitionParameters extends ITCParameters {
    // ITC web form parameter names.
    // These constants must be kept in sync with the web page form.
    // They are used to parse form data.
    public static final String SOURCE_GEOM = "sourceGeom";
    public static final String SOURCE_NORM_PT_SOURCE = "psSourceNorm";
    public static final String SOURCE_NORM_USB = "usbSourceNorm";
    public static final String SOURCE_NORM_GAUSSIAN = "gaussSourceNorm";
    public static final String SOURCE_UNITS_PT_SOURCE = "psSourceUnits";
    public static final String SOURCE_UNITS_USB = "usbSourceUnits";
    public static final String SOURCE_UNITS_GAUSSIAN = "gaussSourceUnits";
    public static final String SOURCE_FWHM_GAUSSIAN = "gaussFwhm";
    public static final String EXTENDED_SOURCE_TYPE = "extSourceType";
    public static final String SOURCE_SPEC = "sourceSpec";
    public static final String ST_SPEC_TYPE = "stSpectrumType";
    public static final String NS_SPEC_TYPE = "nsSpectrumType";
    public static final String RECESSION = "recession";

    public static final String Z = "z";
    public static final String V = "v";

    // ITC web form input values.
    // These constants must be kept in sync with the web page form.
    // They are used to parse form data.
    public static final String POINT_SOURCE = "pointSource";
    public static final String EXTENDED_SOURCE = "extendedSource";
    public static final String UNIFORM = "uniform";
    public static final String GAUSSIAN = "gaussian";

    public static final String MAG = "mag";
    public static final String ABMAG = "ABmag";
    public static final String JY = "jy";
    public static final String WATTS = "watts_fd_wavelength";
    public static final String ERGS_WAVELENGTH = "ergs_fd_wavelength";
    public static final String ERGS_FREQUENCY = "ergs_fd_frequency";
    public static final String MAG_PSA = "mag_per_sq_arcsec";
    public static final String ABMAG_PSA = "ABmag_per_sq_arcsec";
    public static final String JY_PSA = "jy_per_sq_arcsec";
    public static final String WATTS_PSA = "watts_fd_wavelength_per_sq_arcsec";
    public static final String ERGS_WAVELENGTH_PSA = "ergs_fd_wavelength_per_sq_arcsec";
    public static final String ERGS_FREQUENCY_PSA = "ergs_fd_frequency_per_sq_arcsec";
    public static final String WATTS_FLUX = "watts_flux";
    public static final String ERGS_FLUX = "ergs_flux";
    public static final String[] UNITS =
            {MAG, ABMAG, JY, WATTS, ERGS_WAVELENGTH, ERGS_FREQUENCY, MAG_PSA,
                    ABMAG_PSA, JY_PSA, WATTS_PSA, ERGS_WAVELENGTH_PSA, ERGS_FREQUENCY_PSA,
                    WATTS_FLUX, ERGS_FLUX};

    public static final String LIBRARY_STAR = "libraryStar";
    public static final String LIBRARY_NON_STAR = "libraryNonStar";
    public static final String BBTEMP = "BBTemp";
    public static final String BBODY = "modelBlackBody";
    public static final String ELINE = "modelEmLine";
    public static final String PLAW = "modelPowerLaw";
    public static final String LINE_WAVELENGTH = "lineWavelength";
    public static final String LINE_FLUX = "lineFlux";
    public static final String LINE_WIDTH = "lineWidth";
    public static final String LINE_FLUX_UNITS = "lineFluxUnits";
    public static final String LINE_CONTINUUM = "lineContinuum";
    public static final String LINE_CONTINUUM_UNITS = "lineContinuumUnits";
    public static final String PLAW_INDEX = "powerIndex";
    public static final String USER_DEFINED_SPECTRUM = "userDefinedSpectrum";
    public static final String USER_DEFINED_SPECTRUM_NAME = "specUserDef";

    public static final String REDSHIFT = "redshift";
    public static final String VELOCITY = "velocity";

    public static final String SED_FILE_EXTENSION = ".nm";

    /**
     * Location of SED data files
     */
    public static final String STELLAR_LIB = ITCConstants.SED_LIB + "/stellar";
    public static final String NON_STELLAR_LIB = ITCConstants.SED_LIB + "/non_stellar";

    // Data members
    private final String _sourceGeom;  // point or extended
    private String _extSourceType; // uniform, gaussian, ...
    private final double _sourceNorm;  // 19.3 or 2e-17
    private final String _units; // unit code
    private double _fwhm;
    private final WavebandDefinition _normBand; // U, V, B, ...
    private double _bBTemp;
    private double _eLineWavelength;
    private double _eLineWidth;
    private double _eLineFlux;
    private double _eLineContinuumFlux;
    private String _eLineFluxUnits; // units for eline flux
    private String _eLineContinuumFluxUnits;
    private double _pLawIndex;
    private String _sourceSpec;
    private String _specType;
    private String _userDefinedSedString;
    private boolean _isSEDUserDefined = false;

    // resource name of library spectrum
    private String _sedSpectrum;  // /lib/stellar/KOIII.nm

    private double _redshift;  // z

    /**
     * Constructs a SourceDefinitionParameters from a MultipartParser
     *
     * @param p MutipartParser that has all of the parameters and files Parsed
     * @throws Exception of cannot parse any of the parameters.
     */

    public SourceDefinitionParameters(ITCMultiPartParser p) {
        ITCRequest itcR = ITCRequest.from(p); // temporary

        _sourceGeom = p.getParameter(SOURCE_GEOM);
        if (_sourceGeom.equals(POINT_SOURCE)) {
            _sourceNorm = ITCParameters.parseDouble(p.getParameter(SOURCE_NORM_PT_SOURCE), "Integrated Brightness");
            _units = p.getParameter(SOURCE_UNITS_PT_SOURCE);
            if (getUnitCode(_units) < 0) {
                throw new IllegalArgumentException("Unrecognized units: " + _units);
            }
        } else if (_sourceGeom.equals(EXTENDED_SOURCE)) {
            _extSourceType = p.getParameter(EXTENDED_SOURCE_TYPE);
            if (_extSourceType.equals(GAUSSIAN)) {
                _fwhm = ITCParameters.parseDouble(p.getParameter(SOURCE_FWHM_GAUSSIAN), "Full Width Half Max");
                if (_fwhm < 0.1) throw new IllegalArgumentException("Please use a Gaussian FWHM greater than 0.1");
                _sourceNorm = ITCParameters.parseDouble(p.getParameter(SOURCE_NORM_GAUSSIAN), "Integrated Brightness");
                _units = p.getParameter(SOURCE_UNITS_GAUSSIAN);
                if (getUnitCode(_units) < 0) {
                    throw new IllegalArgumentException("Unrecognized units: " + _units);
                }
            } else if (_extSourceType.equals(UNIFORM)) {
                _sourceNorm = ITCParameters.parseDouble(p.getParameter(SOURCE_NORM_USB), "Integrated Brightness");
                _units = p.getParameter(SOURCE_UNITS_USB);
                if (getUnitCode(_units) < 0) {
                    throw new IllegalArgumentException("Unrecognized units: " + _units);
                }
            } else {
                throw new IllegalArgumentException("Unrecognized extended source geometry: " + _extSourceType);
            }
        } else {
            throw new IllegalArgumentException("Unrecognized source geometry: " + getSourceGeometry());
        }

        // Get Normalization info
        _normBand = itcR.enumParameter(WavebandDefinition.class);

        // Get Spectrum Resource
        _sourceSpec = p.getParameter(SOURCE_SPEC);
        if (_sourceSpec.equals(LIBRARY_STAR)) {
            _specType = p.getParameter(ST_SPEC_TYPE);
            _sedSpectrum = STELLAR_LIB + "/" + _specType.toLowerCase() + SED_FILE_EXTENSION;
        } else if (_sourceSpec.equals(LIBRARY_NON_STAR)) {
            _specType = p.getParameter(NS_SPEC_TYPE);
            _sedSpectrum = NON_STELLAR_LIB + "/" + _specType + SED_FILE_EXTENSION;
        } else if (_sourceSpec.equals(ELINE)) {
            _eLineWavelength = ITCParameters.parseDouble(p.getParameter(LINE_WAVELENGTH), "Line Wavelength");
            _eLineWidth = ITCParameters.parseDouble(p.getParameter(LINE_WIDTH), "Line Width");
            _eLineFlux = ITCParameters.parseDouble(p.getParameter(LINE_FLUX), "Line Flux");
            _eLineContinuumFlux = ITCParameters.parseDouble(p.getParameter(LINE_CONTINUUM), "Line Continuum");
            _eLineFluxUnits = p.getParameter(LINE_FLUX_UNITS);
            _eLineContinuumFluxUnits = p.getParameter(LINE_CONTINUUM_UNITS);
            _sourceSpec = ELINE;
            _sedSpectrum = ELINE;
            //if the desired linewidth is too small throw an exception
            //if (_eLineWidth < (3E5 / (_eLineWavelength*1000))) {
            //    throw new Exception("Please use a model line width > 1 nm to avoid undersampling of the line profile when convolved with the transmission response");
            //}
        } else if (_sourceSpec.equals(BBODY)) {
            _bBTemp = ITCParameters.parseDouble(p.getParameter(BBTEMP), "Black Body Temp");
            _sourceSpec = BBODY;
            _sedSpectrum = BBODY;
        } else if (_sourceSpec.equals(PLAW)) {
            _pLawIndex = ITCParameters.parseDouble(p.getParameter(PLAW_INDEX), "Power Law Index");
            _sourceSpec = PLAW;
            _sedSpectrum = PLAW;
        } else if (_sourceSpec.equals(USER_DEFINED_SPECTRUM)) {
            _sourceSpec = USER_DEFINED_SPECTRUM;
            _sedSpectrum = p.getRemoteFileName(USER_DEFINED_SPECTRUM_NAME);
            _userDefinedSedString = p.getTextFile(USER_DEFINED_SPECTRUM_NAME);
            _isSEDUserDefined = true;
        } else {
            throw new IllegalArgumentException("Unrecognized spectrum type: " + _sourceSpec);
        }

        //Get Redshift
        String recession = p.getParameter(RECESSION);
        if (recession.equals(REDSHIFT)) {
            _redshift = ITCParameters.parseDouble(p.getParameter(Z), "Redshift");
        } else if (recession.equals(VELOCITY)) {
            _redshift = ITCParameters.parseDouble(p.getParameter(V), "Redshift Velocity") / ITCConstants.C;
        } else {
            throw new IllegalArgumentException("Unrecognized redshift method: " + recession);
        }
    }

    /**
     * Constructs a SourceDefinitionParameters from a servlet request
     *
     * @throws Exception if input data is not parsable.
     */
    public SourceDefinitionParameters(String sourceGeometry,
                                      String extSourceType,
                                      double sourceNorm,
                                      String units,
                                      double fwhm,
                                      WavebandDefinition normBand,
                                      double redshift,
                                      String spectrumResource,
                                      double bBTemp,
                                      double eLineWavelength,
                                      double eLineWidth,
                                      double eLineFlux,
                                      double eLineContinuumFlux,
                                      String eLineFluxUnits,
                                      String eLineContinuumFluxUnits,
                                      double pLawIndex,
                                      String sourceSpec) {
        _sourceGeom = sourceGeometry;
        _extSourceType = extSourceType;
        _sourceNorm = sourceNorm;
        _units = units;
        _fwhm = fwhm;
        _normBand = normBand;
        _redshift = redshift;
        _sedSpectrum = spectrumResource;
        _bBTemp = bBTemp;
        _eLineWavelength = eLineWavelength;
        _eLineWidth = eLineWidth;
        _eLineFlux = eLineFlux;
        _eLineContinuumFlux = eLineContinuumFlux;
        _eLineFluxUnits = eLineFluxUnits;
        _eLineContinuumFluxUnits = eLineContinuumFluxUnits;
        _pLawIndex = pLawIndex;
        _sourceSpec = sourceSpec;
    }

    public String getSourceGeometry() {
        return _sourceGeom;
    }

    public String getSourceGeometryStr() {
        return "pointSource".equals(_sourceGeom) ? "point source" : "extended source";
    }

    public String getExtendedSourceType() {
        return _extSourceType;
    }

    public double getSourceNormalization() {
        return _sourceNorm;
    }

    public String getUnits() {
        return _units;
    }

    public double getFWHM() {
        return _fwhm;
    }

    public WavebandDefinition getNormBand() {
        return _normBand;
    }

    public double getRedshift() {
        return _redshift;
    }

    public String getSourceSpec() {
        return _sourceSpec;
    }

    public String getSpecType() {
        return _specType;
    }

    public String getSpectrumResource() {
        return _sedSpectrum;
    }

    public double getBBTemp() {
        return _bBTemp;
    }

    public double getELineWavelength() {
        return _eLineWavelength;
    }

    public double getELineWidth() {
        return _eLineWidth;
    }

    public double getELineFlux() {
        return _eLineFlux;
    }

    public double getELineContinuumFlux() {
        return _eLineContinuumFlux;
    }

    public String getELineFluxUnits() {
        return _eLineFluxUnits;
    }

    public String getELineContinuumFluxUnits() {
        return _eLineContinuumFluxUnits;
    }

    public double getPowerLawIndex() {
        return _pLawIndex;
    }

    public boolean isSedUserDefined() {
        return _isSEDUserDefined;
    }

    public String getUserDefinedSpectrum() {
        return _userDefinedSedString;
    }

    // function to be used to Supply human readable units.  Will be printed out in results
    public String getPrettyUnits() {
        if (getUnits().equals(SourceDefinitionParameters.JY))
            return "Jy";
        else
            return getUnits();
    }

    /**
     * Return a human-readable string for debugging
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Source Geometry:\t" + getSourceGeometry() + "\n");
        sb.append("Extended source type:\t" + getExtendedSourceType() + "\n");
        sb.append("Source Normalization:\t" + getSourceNormalization() + "\n");
        sb.append("Units:\t\t\t" + getUnits() + "\n");
        sb.append("Gaussian FWHM:\t" + getFWHM() + "\n");
        sb.append("Normalization Type:\tfilter\n");
        sb.append("Normalization WaveBand:\t" + getNormBand().name + "\n");
        sb.append("Normalization Wavelen:\t0.0\n");
        sb.append("Redshift:\t\t" + getRedshift() + "\n");
        sb.append("Spectrum Resource:\t" + getSpectrumResource() + "\n");
        sb.append("Black Body Temp:\t" + getBBTemp() + "\n");
        sb.append("Emission Line Central Wavelen:\t" + getELineWavelength() + "\n");
        sb.append("Emission Line Width:\t" + getELineWidth() + "\n");
        sb.append("Emission Line Flux:\t" + getELineFlux() + "\n");
        sb.append("Emission Line Continuum Flux:\t" + getELineContinuumFlux() + "\n");
        sb.append("Emission Line Units:" + getELineFluxUnits() + "\n");
        sb.append("Emission Line Cont Units:" + getELineContinuumFluxUnits() + "\n");
        sb.append("Power Law Index:" + getPowerLawIndex() + "\n");
        sb.append("\n");
        return sb.toString();
    }

    public String printParameterSummary() {
        StringBuffer sb = new StringBuffer();

        // This object is used to format numerical strings.
        FormatStringWriter device = new FormatStringWriter();
        device.setPrecision(4);  // four decimal places
        device.clear();

        sb.append("Source spatial profile, brightness, and spectral distribution: \n");
        sb.append("  The z = ");
        sb.append(getRedshift());
        sb.append(" ");
        sb.append(getSourceGeometryStr());
        sb.append(" is a");
        if (getSourceSpec().equals(ELINE)) {
            sb.append("n emission line, at a wavelength of " + device.toString(getELineWavelength()));
            device.setPrecision(2);
            device.clear();
            sb.append(" microns, and with a width of " + device.toString(getELineWidth()) + " km/s.\n  It's total flux is " +
                    device.toString(getELineFlux()) + " " + getELineFluxUnits() + " on a flat continuum of flux density " +
                    device.toString(getELineContinuumFlux()) + " " + getELineContinuumFluxUnits() + ".");
        } else if (getSourceSpec().equals(BBODY)) {
            sb.append(" " + getBBTemp() + "K Blackbody, at " + getSourceNormalization() +
                    " " + getPrettyUnits() + " in the " + getNormBand().name + " band.");
        } else if (getSourceSpec().equals(LIBRARY_STAR)) {
            sb.append(" " + getSourceNormalization() + " " + getPrettyUnits() + " " + getSpecType() +
                    " star in the " + getNormBand().name + " band.");
        } else if (getSourceSpec().equals(LIBRARY_NON_STAR)) {
            sb.append(" " + getSourceNormalization() + " " + getPrettyUnits() + " " + getSpecType() +
                    " in the " + getNormBand().name + " band.");
        } else if (isSedUserDefined()) {
            sb.append(" a user defined spectrum with the name: " + getSpectrumResource());
        } else if (getSourceSpec().equals(PLAW)) {
            sb.append(" Power Law Spectrum, with an index of " + getPowerLawIndex()
                    + " and " + getSourceNormalization() + " mag in the " + getNormBand().name + " band.");
        }
        sb.append("\n");
        return sb.toString();

    }

    /**
     * Returns the unit code corresponding to the specified units string.
     * If the string is not recognized, -1 is returned with no exception.
     */
    public int getUnitCode(String units) {
        if (units == null) return -1;
        for (int i = 0; i < UNITS.length; ++i) {
            if (units.equals(UNITS[i])) return i;
        }
        return -1;
    }
}
