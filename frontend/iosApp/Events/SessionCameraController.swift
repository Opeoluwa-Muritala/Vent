import AVFoundation
import UIKit

final class SessionCameraController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    private let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "app.vent.camera")
    private let onCode: (String) -> Void
    private var preview: AVCaptureVideoPreviewLayer?
    private var device: AVCaptureDevice?
    private var delivered = false
    private var configured = false
    private let frame = CAShapeLayer()
    private let torch = UIButton(type: .system)
    private let message = UILabel()
    private let settings = UIButton(type: .system)

    init(onCode: @escaping (String) -> Void) {
        self.onCode = onCode
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("Use init(onCode:)") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(named: "SplashBackground")
        message.text = "Point at the session QR code."
        message.textColor = .white
        message.numberOfLines = 0
        message.textAlignment = .center
        message.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(message)
        torch.setImage(UIImage(systemName: "flashlight.off.fill"), for: .normal)
        torch.accessibilityLabel = "Torch off"
        torch.addTarget(self, action: #selector(toggleTorch), for: .touchUpInside)
        torch.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(torch)
        settings.setTitle("Open settings", for: .normal)
        settings.addTarget(self, action: #selector(openSettings), for: .touchUpInside)
        settings.translatesAutoresizingMaskIntoConstraints = false
        settings.isHidden = true
        view.addSubview(settings)
        NSLayoutConstraint.activate([
            message.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            message.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            message.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            torch.widthAnchor.constraint(equalToConstant: 48),
            torch.heightAnchor.constraint(equalToConstant: 48),
            torch.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 16),
            torch.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -16),
            settings.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            settings.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            settings.heightAnchor.constraint(greaterThanOrEqualToConstant: 48)
        ])
        frame.fillColor = UIColor.clear.cgColor
        frame.strokeColor = (UIColor(named: "Primary") ?? .systemPurple).cgColor
        frame.lineWidth = 4
        frame.lineCap = .round
        view.layer.addSublayer(frame)
        NotificationCenter.default.addObserver(self, selector: #selector(checkPermission),
            name: UIApplication.didBecomeActiveNotification, object: nil)
        checkPermission()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        if configured { queue.async { [session] in if !session.isRunning { session.startRunning() } } }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        stopCamera()
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
        queue.async { [session] in session.stopRunning() }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview?.frame = view.bounds
        let side = min(240, min(view.bounds.width, view.bounds.height) - 48)
        let bounds = CGRect(x: view.bounds.midX - side / 2, y: view.bounds.midY - side / 2,
            width: side, height: side)
        let path = UIBezierPath()
        for corner in [(bounds.minX, bounds.minY, 1.0, 1.0), (bounds.maxX, bounds.minY, -1.0, 1.0),
                       (bounds.minX, bounds.maxY, 1.0, -1.0), (bounds.maxX, bounds.maxY, -1.0, -1.0)] {
            path.move(to: CGPoint(x: corner.0 + CGFloat(corner.2) * 28, y: corner.1))
            path.addLine(to: CGPoint(x: corner.0, y: corner.1))
            path.addLine(to: CGPoint(x: corner.0, y: corner.1 + CGFloat(corner.3) * 28))
        }
        frame.path = path.cgPath
    }

    @objc private func checkPermission() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: configure()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] _ in
                DispatchQueue.main.async { self?.checkPermission() }
            }
        default:
            message.text = "Camera access is needed to scan a session code. You can also enter the code instead."
            settings.isHidden = false
            torch.isHidden = true
            frame.isHidden = true
        }
    }

    private func configure() {
        guard !configured else {
            queue.async { [session] in if !session.isRunning { session.startRunning() } }
            return
        }
        guard let camera = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: camera) else {
            message.text = "Camera unavailable. Enter the code instead."
            torch.isHidden = true
            return
        }
        let output = AVCaptureMetadataOutput()
        session.beginConfiguration()
        guard session.canAddInput(input), session.canAddOutput(output) else {
            session.commitConfiguration()
            message.text = "Camera unavailable. Enter the code instead."
            return
        }
        session.addInput(input)
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.qr]
        session.commitConfiguration()
        device = camera
        configured = true
        settings.isHidden = true
        frame.isHidden = false
        torch.isHidden = !camera.hasTorch
        message.text = "Point at the session QR code."
        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        layer.frame = view.bounds
        view.layer.insertSublayer(layer, at: 0)
        preview = layer
        queue.async { [session] in session.startRunning() }
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput objects: [AVMetadataObject],
        from connection: AVCaptureConnection) {
        guard !delivered,
              let code = (objects.first as? AVMetadataMachineReadableCodeObject)?.stringValue,
              code.count <= 4096 else { return }
        let digits = code.count == 6 && code.utf8.allSatisfy { (48...57).contains($0) }
        let signed = code.split(separator: ".").count == 3 && code.utf8.allSatisfy {
            (48...57).contains($0) || (65...90).contains($0) || (97...122).contains($0) || [45, 46, 95].contains($0)
        }
        guard digits || signed else { return }
        delivered = true
        stopCamera()
        onCode(code)
    }

    private func stopCamera() {
        if let camera = device, camera.hasTorch, (try? camera.lockForConfiguration()) != nil {
            camera.torchMode = .off
            camera.unlockForConfiguration()
        }
        queue.async { [session] in if session.isRunning { session.stopRunning() } }
    }

    @objc private func toggleTorch() {
        guard let camera = device, camera.hasTorch else { return }
        do {
            try camera.lockForConfiguration()
            camera.torchMode = camera.torchMode == .on ? .off : .on
            camera.unlockForConfiguration()
            let enabled = camera.torchMode == .on
            torch.setImage(UIImage(systemName: enabled ? "flashlight.on.fill" : "flashlight.off.fill"), for: .normal)
            torch.accessibilityLabel = enabled ? "Torch on" : "Torch off"
        } catch {
            message.text = "Torch unavailable. You can still scan the code."
        }
    }

    @objc private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}
